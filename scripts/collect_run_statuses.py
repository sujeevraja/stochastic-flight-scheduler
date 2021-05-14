#!/usr/bin/env python3

import os
import shutil


def get_base_path():
    return os.path.abspath(os.path.dirname(__file__))


def get_failed_logs():
    base = os.path.join(get_base_path(), "output")
    with open(os.path.join(base, "timed_out.txt"), "r") as infile:
        return [os.path.join(base, l.strip()) for l in infile.readlines()]


def get_failed_command(log_path):
    with open(log_path, "r") as logfile:
        line = logfile.readlines()[1].strip()
        return line.split(":")[-1].strip()


def get_solution_name(log_path):
    with open(log_path, "r") as logfile:
        log_line = logfile.readlines()[1].strip()
        return log_line.split(" ")[-1]


def main():
    failed_commands = []
    for log_path in get_failed_logs():
        cmd = get_failed_command(log_path)
        print(cmd)


if __name__ == '__main__':
    main()
