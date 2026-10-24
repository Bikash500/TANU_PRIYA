import { spawn } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const serviceDirectory = resolve(projectRoot, "backend", "student-service");
const isWindows = process.platform === "win32";
const command = isWindows ? "cmd.exe" : "./mvnw";
const args = isWindows
  ? ["/d", "/s", "/c", "mvnw.cmd spring-boot:run"]
  : ["spring-boot:run"];
const child = spawn(command, args, {
  cwd: serviceDirectory,
  stdio: "inherit",
});

child.on("error", (error) => {
  console.error("Could not start the Spring Boot student service.", error);
  process.exitCode = 1;
});

child.on("exit", (code, signal) => {
  process.exitCode = code ?? (signal ? 1 : 0);
});
