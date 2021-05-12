#!/usr/bin/env python3

import logging
import typing


def get_run_lines(instances: typing.List[str], prefix: str, key: str,
                  values: typing.List[str], quality: bool) -> typing.List[str]:
    base_cmd = [
        "python",
        "batch_runner.py",
        "-k", key,
    ]
    lines: typing.List[str] = []
    counter = 0
    for name in instances:
        cmd = [c for c in base_cmd]
        cmd.extend(["-i", name])
        if quality:
            for value in values:
                run_cmd = [c for c in cmd]
                run_cmd.extend([
                    "-q", str(value),
                    "-x", f"{prefix}_{counter}",
                ])
                lines.append(" ".join(run_cmd))
                counter += 1
        else:
            cmd.extend([
                "-t", " ".join(values),
                "-x", f"{prefix}_{counter}",
            ])
            lines.append(" ".join(cmd))
            counter += 1
    return lines


def main():
    logging.basicConfig(format='%(asctime)s %(levelname)s--: %(message)s',
                        level=logging.DEBUG)
    instances = [f"s{i}" for i in range(1, 7)]
    arg_tups = [
        ("budget", "r", ["0.25", "0.5", "0.75", "1", "2"], True),
        ("column_caching", "cache", ["y", "n"], False),
        ("mean", "m", ["15", "30", "45", "60"], True),
        ("thread", "parallel", ["1", "10", "20", "30"], False),
        ("quality", "f", ["hub", "rush"], True),
        ("scenario", "numScenarios", ["10", "20", "30", "40", "50"], True),
        ("column", "c", ["enum", "all", "best", "first"], False),
        ("cut", "cut", ["single", "multi"], False)
    ]

    lines = []
    for args in arg_tups:
        lines.extend(get_run_lines(instances, *args))

    with open("submit_runs.sh", "w") as script_file:
        for line in lines:
            script_file.write(line)
            script_file.write("\n")


if __name__ == '__main__':
    main()
