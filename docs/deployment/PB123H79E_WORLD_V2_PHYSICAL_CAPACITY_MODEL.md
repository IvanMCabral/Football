# World V2 Physical Capacity Model

Admission separates three quantities:

1. serialized payload bytes;
2. conservative local Redis physical bytes, including key/string overhead;
3. provider safety reserve.

The local accounting uncertainty margin is 65,536 bytes and the provider margin is 32,768 bytes. They are independent and both are deducted before admission.

Fresh representative measurement:

| Representation | Serialized bytes | Redis MEMORY USAGE |
|---|---:|---:|
| Legacy | 501,901 | 501,992 |
| PREPARED | 90,688 | 90,790 |
| Catalog | 501,847 | 502,031 |
| COMMITTED | 94,515 | 94,604 |

For that fixture, planned peak was 268,232,274 bytes and empirical physical peak was 268,231,944 bytes. The model overestimated the measured peak by 330 bytes before the separate margins.

The reproducible N=500 fuzz admitted 400 and blocked 100 worlds. Maximum `physical - planned` among admitted cases was -388 bytes; unsafe admissions were 0.

The multidimensional stress search varied players, aliases, custom players, custom teams, 512-character Unicode text, 10 skills, 32 traits per player, league deltas, removals, and relations. The worst admitted scale was 35 with planned 268,336,742 and physical 268,336,352 bytes. Scale 36 was the first blocked fixture. With the 268,435,456-byte quota, remaining headroom was 98,714 bytes, covering the 32,768-byte provider reserve plus the 65,536-byte local margin.

Provider accounting remains `UNVERIFIED`; no Upstash operation was performed.
