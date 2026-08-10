#!/usr/bin/env python3
"""Assign and verify Android's Assistant role without leaking adb failures through a shell."""

from __future__ import annotations

import re
import subprocess
import sys
import time
from collections.abc import Sequence


ROLE_NAME = "android.app.role.ASSISTANT"
ATTEMPTS = 10
POLL_ATTEMPTS = 20


def adb(*arguments: str, timeout: int = 15) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            ["adb", *arguments],
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as failure:
        stdout = failure.stdout.decode(errors="replace") if isinstance(failure.stdout, bytes) else failure.stdout or ""
        stderr = failure.stderr.decode(errors="replace") if isinstance(failure.stderr, bytes) else failure.stderr or ""
        return subprocess.CompletedProcess(["adb", *arguments], 124, stdout, stderr or "adb command timed out")


def output(result: subprocess.CompletedProcess[str]) -> str:
    return "\n".join(part.strip() for part in (result.stdout, result.stderr) if part.strip())


def shell(*arguments: str, timeout: int = 15) -> subprocess.CompletedProcess[str]:
    return adb("shell", *arguments, timeout=timeout)


def print_result(label: str, result: subprocess.CompletedProcess[str], *, tail: int | None = None) -> None:
    text = output(result)
    if tail is not None:
        text = "\n".join(text.splitlines()[-tail:])
    print(f"--- {label} (exit {result.returncode}) ---", file=sys.stderr)
    print(text or "<no output>", file=sys.stderr)


def contains_exact_line(text: str, expected: str) -> bool:
    return expected in {line.strip() for line in text.splitlines()}


def acceptable_component(value: str, package_name: str, class_name: str) -> bool:
    short = f"{package_name}/.{class_name}"
    full = f"{package_name}/app.chalna.capture.{class_name}"
    return value in {short, full}


def diagnostics(package_name: str, last_role_result: subprocess.CompletedProcess[str]) -> None:
    print_result("last add-role-holder", last_role_result)
    sdk = output(shell("getprop", "ro.build.version.sdk"))
    low_ram = output(shell("getprop", "ro.config.low_ram"))
    print(f"sdk={sdk or '<unknown>'} low_ram={low_ram or '<unset>'}", file=sys.stderr)

    queries: Sequence[tuple[str, subprocess.CompletedProcess[str], int | None]] = (
        (
            "ACTION_ASSIST candidates",
            shell(
                "cmd",
                "package",
                "query-activities",
                "--brief",
                "-a",
                "android.intent.action.ASSIST",
                "-c",
                "android.intent.category.DEFAULT",
                package_name,
            ),
            None,
        ),
        ("Assistant role holders", shell("cmd", "role", "get-role-holders", "--user", "0", ROLE_NAME), None),
        ("role service", shell("dumpsys", "role", timeout=30), 220),
        ("package", shell("dumpsys", "package", package_name, timeout=30), 400),
        ("logcat", adb("logcat", "-d", "-v", "brief", timeout=30), 500),
    )
    package_pattern = re.compile(
        r"android\.intent\.action\.ASSIST|VoiceInteractionService|AssistFallbackActivity|ChalnaRecognitionService",
        re.IGNORECASE,
    )
    role_pattern = re.compile(
        r"AssistantRoleBehavior|RoleController|RoleManager|VoiceInteraction|PermissionController|role",
        re.IGNORECASE,
    )
    for label, result, tail in queries:
        text = output(result)
        if label == "package":
            lines = text.splitlines()
            selected: list[str] = []
            for index, line in enumerate(lines):
                if package_pattern.search(line):
                    selected.extend(lines[max(0, index - 3) : min(len(lines), index + 9)])
            text = "\n".join(dict.fromkeys(selected)) or text
        elif label == "logcat":
            text = "\n".join(line for line in text.splitlines() if role_pattern.search(line))
            if tail is not None:
                text = "\n".join(text.splitlines()[-tail:])
            tail = None
        print_result(label, subprocess.CompletedProcess(result.args, result.returncode, text, ""), tail=tail)


def main() -> int:
    if len(sys.argv) != 2 or not sys.argv[1].strip():
        print("usage: verify-assistant-role.py PACKAGE_NAME", file=sys.stderr)
        return 2
    package_name = sys.argv[1].strip()
    adb("logcat", "-c")

    last = subprocess.CompletedProcess([], 1, "", "role command was not executed")
    for attempt in range(1, ATTEMPTS + 1):
        last = shell("cmd", "role", "add-role-holder", "--user", "0", ROLE_NAME, package_name)
        if last.returncode == 0:
            break
        print(f"Assistant role attempt {attempt}/{ATTEMPTS} exited {last.returncode}", file=sys.stderr)
        if attempt < ATTEMPTS:
            time.sleep(2)
    else:
        diagnostics(package_name, last)
        return 1

    holders = ""
    for _ in range(POLL_ATTEMPTS):
        holders_result = shell("cmd", "role", "get-role-holders", "--user", "0", ROLE_NAME)
        holders = output(holders_result)
        if holders_result.returncode == 0 and contains_exact_line(holders, package_name):
            break
        time.sleep(1)
    else:
        print(f"Assistant role transaction did not retain {package_name}. holders={holders}", file=sys.stderr)
        diagnostics(package_name, last)
        return 1

    interactor = recognizer = assistant = ""
    for _ in range(POLL_ATTEMPTS):
        interactor = output(shell("settings", "get", "secure", "voice_interaction_service"))
        recognizer = output(shell("settings", "get", "secure", "voice_recognition_service"))
        assistant = output(shell("settings", "get", "secure", "assistant"))
        if acceptable_component(interactor, package_name, "assistant.ChalnaVoiceInteractionService") and acceptable_component(
            recognizer,
            package_name,
            "assistant.ChalnaRecognitionService",
        ):
            print(f"Assistant role wired to {interactor}")
            print(f"Recognition service wired to {recognizer}")
            return 0
        if acceptable_component(assistant, package_name, "assistant.AssistFallbackActivity"):
            print(f"Assistant activity fallback wired to {assistant}")
            return 0
        time.sleep(1)

    print("Assistant role holder was accepted but platform wiring did not converge.", file=sys.stderr)
    print(f"voice_interaction_service={interactor}", file=sys.stderr)
    print(f"voice_recognition_service={recognizer}", file=sys.stderr)
    print(f"assistant={assistant}", file=sys.stderr)
    diagnostics(package_name, last)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
