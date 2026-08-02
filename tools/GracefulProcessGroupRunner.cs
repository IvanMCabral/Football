using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

internal static class GracefulProcessGroupRunner
{
    private const uint CREATE_NEW_PROCESS_GROUP = 0x00000200;
    private const uint CREATE_NEW_CONSOLE = 0x00000010;
    private const uint STARTF_USESTDHANDLES = 0x00000100;
    private const uint CTRL_C_EVENT = 0;
    private const uint CTRL_BREAK_EVENT = 1;
    private const uint HANDLE_FLAG_INHERIT = 1;
    private const int WAIT_OBJECT_0 = 0;
    private const int WAIT_TIMEOUT = 0x00000102;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct STARTUPINFO
    {
        public uint cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public uint dwX;
        public uint dwY;
        public uint dwXSize;
        public uint dwYSize;
        public uint dwXCountChars;
        public uint dwYCountChars;
        public uint dwFillAttribute;
        public uint dwFlags;
        public ushort wShowWindow;
        public ushort cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION
    {
        public IntPtr hProcess;
        public IntPtr hThread;
        public uint dwProcessId;
        public uint dwThreadId;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CreateProcess(
        string lpApplicationName,
        string lpCommandLine,
        IntPtr lpProcessAttributes,
        IntPtr lpThreadAttributes,
        bool bInheritHandles,
        uint dwCreationFlags,
        IntPtr lpEnvironment,
        string lpCurrentDirectory,
        ref STARTUPINFO lpStartupInfo,
        out PROCESS_INFORMATION lpProcessInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GenerateConsoleCtrlEvent(uint dwCtrlEvent, uint dwProcessGroupId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetConsoleCtrlHandler(IntPtr handlerRoutine, bool add);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool FreeConsole();

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool AttachConsole(uint dwProcessId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint WaitForSingleObject(IntPtr hHandle, uint dwMilliseconds);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetExitCodeProcess(IntPtr hProcess, out uint lpExitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool TerminateProcess(IntPtr hProcess, uint uExitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr hObject);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetHandleInformation(IntPtr hObject, uint dwMask, uint dwFlags);

    private static int Main(string[] args)
    {
        try
        {
            Dictionary<string, string> options = Parse(args);
            if (options.ContainsKey("signal-existing"))
            {
                return SignalExisting(options);
            }
            string command = Require(options, "command");
            string signalFile = Require(options, "signal-file");
            string pidFile = Require(options, "pid-file");
            string stateFile = options.ContainsKey("state-file") ? options["state-file"] : string.Empty;
            string resultFile = Require(options, "result-file");
            string stdoutPath = Require(options, "stdout");
            string stderrPath = Require(options, "stderr");
            string cwd = options.ContainsKey("cwd") ? options["cwd"] : Environment.CurrentDirectory;
            int timeoutMs = options.ContainsKey("timeout-ms") ? int.Parse(options["timeout-ms"]) : 35000;
            int pollMs = options.ContainsKey("poll-ms") ? int.Parse(options["poll-ms"]) : 100;

            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(stdoutPath)));
            using (FileStream stdout = new FileStream(stdoutPath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite))
            using (FileStream stderr = new FileStream(stderrPath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite))
            {
                SetInheritable(stdout.SafeFileHandle.DangerousGetHandle());
                SetInheritable(stderr.SafeFileHandle.DangerousGetHandle());

                STARTUPINFO startup = new STARTUPINFO();
                startup.cb = (uint)Marshal.SizeOf(typeof(STARTUPINFO));
                startup.dwFlags = STARTF_USESTDHANDLES;
                startup.hStdInput = IntPtr.Zero;
                startup.hStdOutput = stdout.SafeFileHandle.DangerousGetHandle();
                startup.hStdError = stderr.SafeFileHandle.DangerousGetHandle();

                PROCESS_INFORMATION processInfo;
                if (!CreateProcess(null, command, IntPtr.Zero, IntPtr.Zero, true, CREATE_NEW_PROCESS_GROUP | CREATE_NEW_CONSOLE, IntPtr.Zero, cwd, ref startup, out processInfo))
                {
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "CreateProcess failed");
                }

                CloseHandle(processInfo.hThread);
                File.WriteAllText(pidFile, processInfo.dwProcessId.ToString(), Encoding.UTF8);
                if (!string.IsNullOrWhiteSpace(stateFile))
                {
                    using (FileStream stateStream = new FileStream(stateFile, FileMode.Create, FileAccess.Write, FileShare.Read))
                    using (StreamWriter stateWriter = new StreamWriter(stateStream, new UTF8Encoding(false)))
                    {
                        stateWriter.Write(
                            "{" +
                            "\"javaPid\":" + processInfo.dwProcessId + "," +
                            "\"processGroupId\":" + processInfo.dwProcessId + "," +
                            "\"created\":true," +
                            "\"consoleReady\":true" +
                            "}");
                        stateWriter.Flush();
                        stateStream.Flush(true);
                    }
                }

                bool gracefulSignalSent = false;
                bool gracefulShutdownObserved = false;
                bool forceKillUsed = false;
                bool ctrlCResult = false;
                bool ctrlBreakResult = false;
                bool fallbackUsed = false;
                string signalAttempted = "NONE";
                string signalUsed = "NONE";
                int ctrlCError = 0;
                int ctrlBreakError = 0;
                uint javaExitCode = 0;
                long shutdownDurationMs = 0;
                DateTime startedShutdownAt = DateTime.MinValue;

                try
                {
                    while (true)
                    {
                        uint wait = WaitForSingleObject(processInfo.hProcess, (uint)pollMs);
                        if (wait == WAIT_OBJECT_0)
                        {
                            gracefulShutdownObserved = gracefulSignalSent;
                            break;
                        }
                        if (wait != WAIT_TIMEOUT)
                        {
                            throw new Win32Exception(Marshal.GetLastWin32Error(), "WaitForSingleObject failed");
                        }

                        if (!gracefulSignalSent && File.Exists(signalFile))
                        {
                            startedShutdownAt = DateTime.UtcNow;
                            SetConsoleCtrlHandler(IntPtr.Zero, true);
                            FreeConsole();
                            bool attached = AttachConsole(processInfo.dwProcessId);
                            if (!attached)
                            {
                                throw new Win32Exception(Marshal.GetLastWin32Error(), "AttachConsole to child process failed");
                            }
                            signalAttempted = "CTRL_C_EVENT";
                            ctrlCResult = GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0);
                            if (!ctrlCResult)
                            {
                                ctrlCError = Marshal.GetLastWin32Error();
                                fallbackUsed = true;
                                ctrlBreakResult = GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0);
                                if (!ctrlBreakResult)
                                {
                                    ctrlBreakError = Marshal.GetLastWin32Error();
                                }
                            }
                            FreeConsole();
                            gracefulSignalSent = ctrlCResult || ctrlBreakResult;
                            signalUsed = ctrlCResult ? "CTRL_C_EVENT" : (ctrlBreakResult ? "CTRL_BREAK_EVENT" : "NONE");
                            if (!gracefulSignalSent)
                            {
                                throw new Win32Exception(Marshal.GetLastWin32Error(), "GenerateConsoleCtrlEvent CTRL_BREAK_EVENT failed");
                            }
                        }

                        if (gracefulSignalSent && (DateTime.UtcNow - startedShutdownAt).TotalMilliseconds > timeoutMs)
                        {
                            forceKillUsed = true;
                            TerminateProcess(processInfo.hProcess, 57005);
                            WaitForSingleObject(processInfo.hProcess, 5000);
                            break;
                        }
                    }

                    if (gracefulSignalSent && startedShutdownAt != DateTime.MinValue)
                    {
                        shutdownDurationMs = (long)(DateTime.UtcNow - startedShutdownAt).TotalMilliseconds;
                    }

                    GetExitCodeProcess(processInfo.hProcess, out javaExitCode);
                    string status = gracefulSignalSent && gracefulShutdownObserved && !forceKillUsed ? "PASS" : "FAIL";
                    File.WriteAllText(
                        resultFile,
                        Json(status, processInfo.dwProcessId, gracefulSignalSent, gracefulShutdownObserved, forceKillUsed,
                            shutdownDurationMs, javaExitCode, signalAttempted, signalUsed, fallbackUsed, ctrlCResult,
                            ctrlBreakResult, ctrlCError, ctrlBreakError, startedShutdownAt),
                        Encoding.UTF8);
                    return status == "PASS" ? 0 : 2;
                }
                finally
                {
                    CloseHandle(processInfo.hProcess);
                }
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex.GetType().Name + ": " + ex.Message);
            return 1;
        }
    }

    private static int SignalExisting(Dictionary<string, string> options)
    {
        uint javaPid = uint.Parse(Require(options, "java-pid"));
        uint processGroupId = uint.Parse(Require(options, "process-group-id"));
        string resultFile = Require(options, "result-file");
        int timeoutMs = options.ContainsKey("timeout-ms") ? int.Parse(options["timeout-ms"]) : 35000;
        bool simulateSignalFail = options.ContainsKey("simulate-signal-fail");

        bool gracefulSignalSent = false;
        bool gracefulShutdownObserved = false;
        bool forceKillUsed = false;
        bool ctrlCResult = false;
        bool ctrlBreakResult = false;
        bool fallbackUsed = false;
        string signalAttempted = "NONE";
        string signalUsed = "NONE";
        int ctrlCError = 0;
        int ctrlBreakError = 0;
        uint javaExitCode = 0;
        DateTime startedShutdownAt = DateTime.UtcNow;

        try
        {
            Process java = Process.GetProcessById((int)javaPid);
            if (simulateSignalFail)
            {
                signalAttempted = "SIMULATED_FAILURE";
            }
            else
            {
                SetConsoleCtrlHandler(IntPtr.Zero, true);
                FreeConsole();
                bool attached = AttachConsole(javaPid);
                if (!attached)
                {
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "AttachConsole to existing Java process failed");
                }
                signalAttempted = "CTRL_C_EVENT";
                ctrlCResult = GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0);
                if (!ctrlCResult)
                {
                    ctrlCError = Marshal.GetLastWin32Error();
                    fallbackUsed = true;
                    ctrlBreakResult = GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0);
                    if (!ctrlBreakResult)
                    {
                        ctrlBreakError = Marshal.GetLastWin32Error();
                    }
                }
                FreeConsole();
                gracefulSignalSent = ctrlCResult || ctrlBreakResult;
                signalUsed = ctrlCResult ? "CTRL_C_EVENT" : (ctrlBreakResult ? "CTRL_BREAK_EVENT" : "NONE");
            }

            if (gracefulSignalSent)
            {
                gracefulShutdownObserved = java.WaitForExit(timeoutMs);
            }
            if (!java.HasExited)
            {
                java.Refresh();
            }
            if (java.HasExited)
            {
                javaExitCode = unchecked((uint)java.ExitCode);
            }
            string status = gracefulSignalSent && gracefulShutdownObserved && !forceKillUsed ? "PASS" : "FAIL";
            File.WriteAllText(
                resultFile,
                Json(status, javaPid, gracefulSignalSent, gracefulShutdownObserved, forceKillUsed,
                    (long)(DateTime.UtcNow - startedShutdownAt).TotalMilliseconds, javaExitCode, signalAttempted, signalUsed,
                    fallbackUsed, ctrlCResult, ctrlBreakResult, ctrlCError, ctrlBreakError, startedShutdownAt),
                Encoding.UTF8);
            return status == "PASS" ? 0 : 2;
        }
        catch (Exception ex)
        {
            File.WriteAllText(
                resultFile,
                "{" +
                "\"status\":\"FAIL\"," +
                "\"javaPid\":" + javaPid + "," +
                "\"processGroupId\":" + processGroupId + "," +
                "\"signalAttempted\":\"" + signalAttempted + "\"," +
                "\"signalUsed\":\"NONE\"," +
                "\"error\":\"" + Escape(ex.Message) + "\"," +
                "\"gracefulSignalSent\":false," +
                "\"gracefulShutdownObserved\":false," +
                "\"forceKillUsed\":false" +
                "}",
                Encoding.UTF8);
            return 1;
        }
    }

    private static Dictionary<string, string> Parse(string[] args)
    {
        Dictionary<string, string> values = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        for (int i = 0; i < args.Length; i++)
        {
            if (!args[i].StartsWith("--", StringComparison.Ordinal))
            {
                continue;
            }
            string key = args[i].Substring(2);
            if (i + 1 >= args.Length)
            {
                throw new ArgumentException("Missing value for --" + key);
            }
            values[key] = args[++i];
        }
        return values;
    }

    private static string Require(Dictionary<string, string> values, string key)
    {
        if (!values.ContainsKey(key) || string.IsNullOrWhiteSpace(values[key]))
        {
            throw new ArgumentException("Missing --" + key);
        }
        return values[key];
    }

    private static void SetInheritable(IntPtr handle)
    {
        if (!SetHandleInformation(handle, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT))
        {
            throw new Win32Exception(Marshal.GetLastWin32Error(), "SetHandleInformation failed");
        }
    }

    private static string Json(
        string status,
        uint javaPid,
        bool signalSent,
        bool observed,
        bool forceKill,
        long durationMs,
        uint exitCode,
        string signalAttempted,
        string signalUsed,
        bool fallbackUsed,
        bool ctrlCResult,
        bool ctrlBreakResult,
        int ctrlCError,
        int ctrlBreakError,
        DateTime signalTimestamp)
    {
        return "{" +
            "\"status\":\"" + status + "\"," +
            "\"javaPid\":" + javaPid + "," +
            "\"processGroupId\":" + javaPid + "," +
            "\"signalAttempted\":\"" + signalAttempted + "\"," +
            "\"signalUsed\":\"" + signalUsed + "\"," +
            "\"fallbackUsed\":" + Bool(fallbackUsed) + "," +
            "\"signalFallbackUsed\":" + Bool(fallbackUsed) + "," +
            "\"ctrlCResult\":" + Bool(ctrlCResult) + "," +
            "\"ctrlBreakResult\":" + Bool(ctrlBreakResult) + "," +
            "\"ctrlCWin32Error\":" + ctrlCError + "," +
            "\"ctrlCError\":" + ctrlCError + "," +
            "\"ctrlBreakWin32Error\":" + ctrlBreakError + "," +
            "\"ctrlBreakError\":" + ctrlBreakError + "," +
            "\"signalTimestampUtc\":\"" + (signalTimestamp == DateTime.MinValue ? "" : signalTimestamp.ToString("O")) + "\"," +
            "\"gracefulSignalSent\":" + Bool(signalSent) + "," +
            "\"gracefulShutdownObserved\":" + Bool(observed) + "," +
            "\"forceKillUsed\":" + Bool(forceKill) + "," +
            "\"shutdownDurationMs\":" + durationMs + "," +
            "\"javaExitCode\":" + exitCode +
            "}";
    }

    private static string Bool(bool value)
    {
        return value ? "true" : "false";
    }

    private static string Escape(string value)
    {
        return value.Replace("\\", "\\\\").Replace("\"", "\\\"");
    }
}
