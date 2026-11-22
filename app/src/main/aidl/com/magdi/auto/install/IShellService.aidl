package com.magdi.auto.install;

// Runs inside the Shizuku "shell" process (uid 2000). Every method returns "<exit code>\n<output>".
interface IShellService {
    void destroy() = 16777114; // transaction code reserved by Shizuku
    int uid() = 1;
    String openInstaller(String uri, String component, int userId) = 4;
}
