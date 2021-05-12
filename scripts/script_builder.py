#!/usr/bin/env python3

import logging


def get_run_lines(instances, prefix, key, values, quality):
    base_cmd = [
        "python3",
        "batch_runner.py",
        "-k", key,
    ]
    lines = []
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
    arg_tups = [
        ("budget", "r", ["0.25", "0.5", "0.75", "1", "2"], True),
        ("column_caching", "cache", ["y", "n"], False),
        ("mean", "m", ["15", "30", "45", "60"], True),
        ("thread", "parallel", ["1", "10", "20", "30"], False),
        ("quality", "f", ["hub", "rush"], True),
        ("column", "c", ["enum", "all", "best", "first"], False),
        ("cut", "cut", ["single", "multi"], False),
    ]

    # Runs to get s6 results for all tables.
    lines = []
    for args in arg_tups:
        lines.extend(get_run_lines(["s6"], *args))

    # Runs to get all instance results for scenario table.
    args = ("scenario", "numScenarios", ["10", "20", "30", "40", "50"], True)
    instances = [f"s{i}" for i in range(1, 7)]
    lines.extend(get_run_lines(instances, *args))

    with open("submit_runs.sh", "w") as script_file:
        for line in lines:
            script_file.write(line)
            script_file.write("\n")


if __name__ == '__main__':
    main()
