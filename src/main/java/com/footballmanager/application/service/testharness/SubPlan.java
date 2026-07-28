package com.footballmanager.application.service.testharness;

record SubPlan(
    String playerOffId,
    String playerOnId,
    String offName,
    String onName,
    String offPosition,
    String onPosition,
    int scoreDelta
) {
}
