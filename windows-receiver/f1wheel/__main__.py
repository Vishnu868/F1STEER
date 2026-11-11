"""Entry point:  python -m f1wheel  [--nogui] [--backend vigem|vjoy|none]"""
from __future__ import annotations

import argparse
import logging
import sys
import time

from .config import Config, config_dir
from .engine import Receiver, local_ipv4s


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="f1wheel", description="DIY F1 Wheel receiver")
    ap.add_argument("--nogui", action="store_true", help="run without the dashboard")
    ap.add_argument("--backend", choices=["vigem", "vjoy", "none"], help="override controller type")
    ap.add_argument("--port", type=int, help="override UDP port")
    args = ap.parse_args(argv)

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        handlers=[logging.FileHandler(config_dir() / "receiver.log", encoding="utf-8")]
        + ([logging.StreamHandler(sys.stdout)] if args.nogui and sys.stdout else []),
    )
    cfg = Config.load()
    if args.backend:
        cfg.backend = args.backend
    if args.port:
        cfg.port = args.port

    rx = Receiver(cfg)
    rx.open_backend()
    try:
        rx.start()
    except OSError as e:
        logging.error("Cannot open UDP port %s: %s", cfg.port, e)
        if args.nogui:
            return 2

    if args.nogui:
        print(f"Listening on {', '.join(local_ipv4s())} port {cfg.port}  PIN {cfg.pin}  (Ctrl+C to quit)")
        try:
            while True:
                time.sleep(1)
                s = rx.snapshot()
                st, th, br, _ = s["out"]
                print(f"\r{'LIVE' if s['live'] else 'idle'} steer {st:+.2f} thr {th:.2f} brk {br:.2f} "
                      f"rx {s['rx_hz']:.0f}/s rtt {s['rtt'] or 0:.1f}ms drop {s['dropped']}   ", end="")
        except KeyboardInterrupt:
            pass
        finally:
            rx.shutdown()
        return 0

    from .ui import Dashboard
    Dashboard(rx).run()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
