"""Tkinter dashboard for the receiver (stdlib only, no extra dependencies)."""
from __future__ import annotations

import math
import threading
import tkinter as tk
from tkinter import messagebox, ttk

from . import protocol as P
from .config import DEFAULT_MAPPING, XBOX_BUTTONS, new_pin
from .engine import Receiver, local_ipv4s

BG = "#0d0f14"
PANEL = "#161a22"
FG = "#e8ebf1"
MUTED = "#8a93a6"
RED = "#e10600"
GREEN = "#1ed760"
AMBER = "#ffb000"
BLUE = "#2f8bff"

FUNCTION_LABELS = {
    "gear_up": "Gear up (right paddle)",
    "gear_down": "Gear down (left paddle)",
    "drs": "DRS",
    "ers": "ERS / Overtake",
    "clutch": "Clutch",
    "pause": "Pause",
    "look_back": "Look back",
    "flashback": "Flashback",
    "pit_limiter": "Pit limiter",
    "mfd": "MFD / Radio",
}


class Bar(tk.Canvas):
    """Horizontal bar. centered=True draws a -1..+1 bar from the middle."""

    def __init__(self, master, color, centered=False, height=22, **kw):
        super().__init__(master, height=height, bg="#0a0c10", highlightthickness=0, **kw)
        self.color, self.centered = color, centered
        self.bind("<Configure>", lambda e: self.set(getattr(self, "_v", 0.0)))

    def set(self, v: float) -> None:
        self._v = v
        w, h = max(self.winfo_width(), 2), max(self.winfo_height(), 2)
        self.delete("all")
        if self.centered:
            mid = w / 2
            x = mid + v * mid
            self.create_rectangle(min(mid, x), 3, max(mid, x), h - 3, fill=self.color, width=0)
            self.create_line(mid, 0, mid, h, fill=MUTED)
        else:
            self.create_rectangle(0, 3, v * w, h - 3, fill=self.color, width=0)


class Lamp(tk.Label):
    def __init__(self, master, text):
        super().__init__(master, text=text, bg="#222733", fg=MUTED, padx=6, pady=3,
                         font=("Segoe UI", 9, "bold"))

    def set(self, on: bool, color=GREEN):
        self.configure(bg=color if on else "#222733", fg="#000" if on else MUTED)


class Dashboard:
    def __init__(self, rx: Receiver):
        self.rx = rx
        self.root = tk.Tk()
        self.root.title("DIY F1 Wheel - Receiver")
        self.root.configure(bg=BG)
        self.root.minsize(860, 600)
        self._style()
        self._build()
        self.root.protocol("WM_DELETE_WINDOW", self._quit)
        self._refresh()

    # ----------------------------------------------------------------- layout
    def _style(self):
        s = ttk.Style(self.root)
        s.theme_use("clam")
        s.configure(".", background=BG, foreground=FG, font=("Segoe UI", 10))
        s.configure("Panel.TFrame", background=PANEL)
        s.configure("Panel.TLabel", background=PANEL, foreground=FG)
        s.configure("Muted.TLabel", background=PANEL, foreground=MUTED)
        s.configure("Big.TLabel", background=PANEL, foreground=FG, font=("Segoe UI", 18, "bold"))
        s.configure("Title.TLabel", background=BG, foreground=FG, font=("Segoe UI", 16, "bold"))
        s.configure("TButton", background="#232938", foreground=FG, borderwidth=0, padding=6)
        s.map("TButton", background=[("active", "#2f3647")])
        s.configure("Accent.TButton", background=RED, foreground="#fff")
        s.map("Accent.TButton", background=[("active", "#ff2a1f")])

    def _panel(self, parent, title):
        f = ttk.Frame(parent, style="Panel.TFrame", padding=12)
        ttk.Label(f, text=title.upper(), style="Muted.TLabel",
                  font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(0, 6))
        return f

    def _build(self):
        top = tk.Frame(self.root, bg=BG)
        top.pack(fill="x", padx=14, pady=(12, 6))
        tk.Frame(top, bg=RED, width=6, height=28).pack(side="left", padx=(0, 10))
        ttk.Label(top, text="DIY F1 WHEEL  ·  RECEIVER", style="Title.TLabel").pack(side="left")
        self.btn_server = ttk.Button(top, text="Stop server", command=self._toggle_server,
                                     style="Accent.TButton")
        self.btn_server.pack(side="right")
        ttk.Button(top, text="Settings", command=self._settings).pack(side="right", padx=6)
        ttk.Button(top, text="Diagnostics", command=self._diagnostics).pack(side="right")

        grid = tk.Frame(self.root, bg=BG)
        grid.pack(fill="both", expand=True, padx=14, pady=6)
        grid.columnconfigure((0, 1), weight=1, uniform="c")
        grid.rowconfigure(1, weight=1)

        # --- connection
        c = self._panel(grid, "Connection")
        c.grid(row=0, column=0, sticky="nsew", padx=(0, 6), pady=(0, 6))
        self.lbl_status = ttk.Label(c, text="Waiting for phone", style="Big.TLabel")
        self.lbl_status.pack(anchor="w")
        self.lbl_phone = ttk.Label(c, text="", style="Muted.TLabel")
        self.lbl_phone.pack(anchor="w")
        self.lbl_ip = ttk.Label(c, text="", style="Panel.TLabel", font=("Consolas", 12))
        self.lbl_ip.pack(anchor="w", pady=(8, 0))
        row = ttk.Frame(c, style="Panel.TFrame")
        row.pack(anchor="w", pady=(8, 0))
        ttk.Label(row, text="Pairing PIN", style="Muted.TLabel").pack(side="left")
        self.lbl_pin = ttk.Label(row, text=self.rx.cfg.pin, style="Panel.TLabel",
                                 font=("Consolas", 20, "bold"), foreground=AMBER)
        self.lbl_pin.pack(side="left", padx=10)
        ttk.Button(row, text="New PIN", command=self._new_pin).pack(side="left")

        # --- controller
        k = self._panel(grid, "Virtual controller")
        k.grid(row=0, column=1, sticky="nsew", padx=(6, 0), pady=(0, 6))
        self.lbl_ctrl = ttk.Label(k, text="", style="Big.TLabel")
        self.lbl_ctrl.pack(anchor="w")
        self.lbl_ctrl_err = ttk.Label(k, text="", style="Muted.TLabel", wraplength=380)
        self.lbl_ctrl_err.pack(anchor="w")
        row = ttk.Frame(k, style="Panel.TFrame")
        row.pack(anchor="w", pady=(10, 0))
        ttk.Button(row, text="Reconnect controller", command=self._reconnect).pack(side="left")
        ttk.Button(row, text="Controller test", command=self._sweep_test).pack(side="left", padx=6)
        row2 = ttk.Frame(k, style="Panel.TFrame")
        row2.pack(anchor="w", pady=(8, 0))
        ttk.Label(row2, text="Bind helper:", style="Muted.TLabel").pack(side="left")
        self.bind_var = tk.StringVar(value="gear_up")
        cb = ttk.Combobox(row2, textvariable=self.bind_var, width=16, state="readonly",
                          values=list(FUNCTION_LABELS) + ["steer_left", "steer_right", "throttle", "brake"])
        cb.pack(side="left", padx=6)
        ttk.Button(row2, text="Send in 3 s", command=self._bind_pulse).pack(side="left")
        self.lbl_bind = ttk.Label(k, text="", style="Muted.TLabel")
        self.lbl_bind.pack(anchor="w")

        # --- live inputs
        li = self._panel(grid, "Live inputs  (what the game receives)")
        li.grid(row=1, column=0, sticky="nsew", padx=(0, 6))
        self.lbl_steer = ttk.Label(li, text="Steering", style="Panel.TLabel")
        self.lbl_steer.pack(anchor="w")
        self.bar_steer = Bar(li, BLUE, centered=True, height=30)
        self.bar_steer.pack(fill="x", pady=(2, 10))
        self.lbl_thr = ttk.Label(li, text="Throttle", style="Panel.TLabel")
        self.lbl_thr.pack(anchor="w")
        self.bar_thr = Bar(li, GREEN)
        self.bar_thr.pack(fill="x", pady=(2, 10))
        self.lbl_brk = ttk.Label(li, text="Brake", style="Panel.TLabel")
        self.lbl_brk.pack(anchor="w")
        self.bar_brk = Bar(li, RED)
        self.bar_brk.pack(fill="x", pady=(2, 10))
        self.lbl_gears = ttk.Label(li, text="", style="Panel.TLabel", font=("Segoe UI", 11, "bold"))
        self.lbl_gears.pack(anchor="w", pady=(4, 6))
        lamps = ttk.Frame(li, style="Panel.TFrame")
        lamps.pack(anchor="w")
        self.lamps = {}
        for i, name in enumerate(["gear_up", "gear_down"] + P.BUTTONS):
            lamp = Lamp(lamps, FUNCTION_LABELS[name].split(" (")[0])
            lamp.grid(row=i // 5, column=i % 5, padx=2, pady=2, sticky="ew")
            self.lamps[name] = lamp

        # --- performance
        pf = self._panel(grid, "Performance")
        pf.grid(row=1, column=1, sticky="nsew", padx=(6, 0))
        self.perf = {}
        for key, label in [("rx_hz", "Packets received"), ("sensor_hz", "Phone sensor rate"),
                           ("out_hz", "Controller update rate"), ("rtt", "Round-trip latency"),
                           ("oneway", "Est. one-way latency"), ("dropped", "Dropped packets"),
                           ("rejected", "Rejected packets"), ("interruptions", "Interruptions")]:
            r = ttk.Frame(pf, style="Panel.TFrame")
            r.pack(fill="x", pady=1)
            ttk.Label(r, text=label, style="Muted.TLabel").pack(side="left")
            v = ttk.Label(r, text="-", style="Panel.TLabel", font=("Consolas", 11))
            v.pack(side="right")
            self.perf[key] = v
        ttk.Label(pf, text="EVENTS", style="Muted.TLabel",
                  font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(10, 2))
        self.events = tk.Listbox(pf, height=7, bg="#0a0c10", fg=MUTED, borderwidth=0,
                                 highlightthickness=0, font=("Consolas", 9))
        self.events.pack(fill="both", expand=True)
        self._n_events = 0

    # ---------------------------------------------------------------- refresh
    def _refresh(self):
        s = self.rx.snapshot()
        if not s["running"]:
            self.lbl_status.configure(text="Server stopped", foreground=MUTED)
        elif s["live"]:
            self.lbl_status.configure(text="● Phone connected - LIVE", foreground=GREEN)
        elif s["connected"]:
            self.lbl_status.configure(text="● Phone connected - idle", foreground=AMBER)
        else:
            self.lbl_status.configure(text="Waiting for phone…", foreground=FG)
        self.lbl_phone.configure(text=f"{s['phone_name']}  {s['phone_addr']}" if s["connected"] else
                                 "Open the phone app and enter the IP + PIN below")
        ips = local_ipv4s()
        self.lbl_ip.configure(text="Laptop IP:  " + ("   ".join(ips) if ips else "no network")
                              + f"   port {self.rx.cfg.port}")
        self.btn_server.configure(text="Stop server" if s["running"] else "Start server")

        if s["backend_ok"]:
            self.lbl_ctrl.configure(text=f"● {'Xbox 360 (ViGEm)' if s['backend']=='vigem' else 'vJoy'} active",
                                    foreground=GREEN)
            self.lbl_ctrl_err.configure(text="Windows sees it as a game controller (check joy.cpl).")
        else:
            self.lbl_ctrl.configure(text="● No virtual controller", foreground=RED)
            self.lbl_ctrl_err.configure(text=s["backend_error"] or "Backend disabled in settings.")

        steer, thr, brk, btns = s["out"]
        self.bar_steer.set(steer)
        self.bar_thr.set(thr)
        self.bar_brk.set(brk)
        self.lbl_steer.configure(text=f"Steering  {steer*100:+5.0f}%    (phone angle {s['raw_angle']:+.1f}°)")
        self.lbl_thr.configure(text=f"Throttle  {thr*100:4.0f}%")
        self.lbl_brk.configure(text=f"Brake  {brk*100:4.0f}%")
        self.lbl_gears.configure(text=f"Gear-up presses: {s['gear_up_total']}     "
                                      f"Gear-down presses: {s['gear_down_total']}")
        m = self.rx.cfg.mapping
        for name, lamp in self.lamps.items():
            lamp.set(m.get(name) in btns, AMBER if name.startswith("gear") else GREEN)

        rtt = s["rtt"]
        self.perf["rx_hz"].configure(text=f"{s['rx_hz']:.0f} /s")
        self.perf["sensor_hz"].configure(text=f"{s['sensor_hz']} Hz")
        self.perf["out_hz"].configure(text=f"{s['out_hz']:.0f} Hz")
        self.perf["rtt"].configure(text="-" if rtt is None else f"{rtt:.1f} ms")
        self.perf["oneway"].configure(text="-" if rtt is None else f"~{rtt/2:.1f} ms")
        self.perf["dropped"].configure(text=str(s["dropped"]))
        self.perf["rejected"].configure(text=str(s["rejected"]))
        self.perf["interruptions"].configure(text=str(s["interruptions"]))

        ev = list(self.rx.events)
        if len(ev) != self._n_events:
            self.events.delete(0, "end")
            for e in ev[-60:]:
                self.events.insert("end", e)
            self.events.see("end")
            self._n_events = len(ev)
        self.root.after(50, self._refresh)

    # ---------------------------------------------------------------- actions
    def _toggle_server(self):
        if self.rx.running:
            self.rx.stop()
        else:
            try:
                self.rx.start()
            except OSError as e:
                messagebox.showerror("Cannot start", f"Port {self.rx.cfg.port} is unavailable:\n{e}")

    def _new_pin(self):
        if messagebox.askyesno("New PIN", "Generate a new pairing PIN? The phone will need the new PIN."):
            pin = new_pin()
            self.rx.set_pin(pin)
            self.lbl_pin.configure(text=pin)

    def _reconnect(self):
        threading.Thread(target=self.rx.open_backend, daemon=True).start()

    def _sweep_test(self):
        """Exercise every output so it can be watched in joy.cpl / the game."""
        m = self.rx.cfg.mapping
        steps = []
        for i in range(0, 41):
            v = math.sin(i / 40 * 2 * math.pi)
            steps.append((v, 0, 0, ()))
        steps += [(0, i / 10, 0, ()) for i in range(11)] + [(0, 0, i / 10, ()) for i in range(11)]
        for name in ["gear_up", "gear_down"] + P.BUTTONS:
            steps += [(0, 0, 0, (m[name],))] * 4 + [(0, 0, 0, ())] * 2

        def run(i=0):
            if i >= len(steps):
                return
            st, th, br, b = steps[i]
            self.rx.run_test(st, th, br, b, seconds=0.2)
            self.root.after(50, run, i + 1)
        run()

    def _bind_pulse(self):
        what = self.bind_var.get()
        self.lbl_bind.configure(text="Switch to the game's binding screen now… 3")
        def fire(n=3):
            if n > 0:
                self.lbl_bind.configure(text=f"Switch to the game's binding screen now… {n}")
                self.root.after(1000, fire, n - 1)
                return
            m = self.rx.cfg.mapping
            if what == "steer_left":
                self.rx.run_test(steer=-1, seconds=1.0)
            elif what == "steer_right":
                self.rx.run_test(steer=1, seconds=1.0)
            elif what == "throttle":
                self.rx.run_test(throttle=1, seconds=1.0)
            elif what == "brake":
                self.rx.run_test(brake=1, seconds=1.0)
            else:
                self.rx.run_test(buttons=(m[what],), seconds=0.4)
            self.lbl_bind.configure(text=f"Sent {what}.")
        fire()

    def _settings(self):
        SettingsDialog(self.root, self.rx)

    def _diagnostics(self):
        s = self.rx.snapshot()
        lines = [f"{k}: {v}" for k, v in s.items()]
        lines += ["", f"config: port={self.rx.cfg.port} backend={self.rx.cfg.backend} "
                      f"timeout={self.rx.cfg.timeout_ms}ms", f"local IPs: {local_ipv4s()}", "",
                  "events:"] + list(self.rx.events)
        w = tk.Toplevel(self.root)
        w.title("Diagnostics")
        w.configure(bg=BG)
        t = tk.Text(w, width=100, height=36, bg="#0a0c10", fg=FG, font=("Consolas", 9))
        t.insert("1.0", "\n".join(lines))
        t.pack(fill="both", expand=True)
        def copy():
            self.root.clipboard_clear()
            self.root.clipboard_append("\n".join(lines))
        ttk.Button(w, text="Copy to clipboard", command=copy).pack(pady=6)

    def _quit(self):
        self.rx.shutdown()
        self.root.destroy()

    def run(self):
        self.root.mainloop()


class SettingsDialog:
    def __init__(self, root, rx: Receiver):
        self.rx = rx
        cfg = rx.cfg
        w = self.w = tk.Toplevel(root)
        w.title("Settings")
        w.configure(bg=PANEL)
        w.transient(root)
        f = ttk.Frame(w, style="Panel.TFrame", padding=14)
        f.pack(fill="both", expand=True)

        self.vars = {}
        r = 0
        def row(label, widget):
            nonlocal r
            ttk.Label(f, text=label, style="Panel.TLabel").grid(row=r, column=0, sticky="w", pady=3)
            widget.grid(row=r, column=1, sticky="ew", pady=3, padx=(10, 0))
            r += 1

        self.vars["backend"] = tk.StringVar(value=cfg.backend)
        row("Controller type", ttk.Combobox(f, textvariable=self.vars["backend"], state="readonly",
                                            values=["vigem", "vjoy", "none"], width=18))
        for key, label in [("port", "UDP port"), ("timeout_ms", "Input timeout (ms)"),
                           ("gear_press_ms", "Gear press length (ms)"), ("gear_gap_ms", "Gap between shifts (ms)")]:
            self.vars[key] = tk.StringVar(value=str(getattr(cfg, key)))
            row(label, ttk.Entry(f, textvariable=self.vars[key], width=20))
        self.vars["invert_steering"] = tk.BooleanVar(value=cfg.invert_steering)
        row("Invert steering (PC side)", ttk.Checkbutton(f, variable=self.vars["invert_steering"]))
        self.vars["beacon"] = tk.BooleanVar(value=cfg.beacon)
        row("Auto-discovery broadcast", ttk.Checkbutton(f, variable=self.vars["beacon"]))

        ttk.Label(f, text="BUTTON MAPPING  (phone control → Xbox button)", style="Muted.TLabel",
                  font=("Segoe UI", 9, "bold")).grid(row=r, column=0, columnspan=2, sticky="w", pady=(12, 4))
        r += 1
        self.map_vars = {}
        for name in DEFAULT_MAPPING:
            v = tk.StringVar(value=cfg.mapping.get(name, "NONE"))
            self.map_vars[name] = v
            row(FUNCTION_LABELS[name], ttk.Combobox(f, textvariable=v, state="readonly",
                                                   values=XBOX_BUTTONS, width=18))
        b = ttk.Frame(f, style="Panel.TFrame")
        b.grid(row=r, column=0, columnspan=2, pady=(12, 0), sticky="e")
        ttk.Button(b, text="Defaults", command=self._defaults).pack(side="left")
        ttk.Button(b, text="Cancel", command=w.destroy).pack(side="left", padx=6)
        ttk.Button(b, text="Save", style="Accent.TButton", command=self._save).pack(side="left")

    def _defaults(self):
        for k, v in DEFAULT_MAPPING.items():
            self.map_vars[k].set(v)

    def _save(self):
        cfg = self.rx.cfg
        try:
            port = int(self.vars["port"].get())
            timeout = int(self.vars["timeout_ms"].get())
            press = int(self.vars["gear_press_ms"].get())
            gap = int(self.vars["gear_gap_ms"].get())
            if not (1024 <= port <= 65535 and 50 <= timeout <= 2000 and 20 <= press <= 500 and 20 <= gap <= 500):
                raise ValueError
        except ValueError:
            messagebox.showerror("Invalid value", "Port 1024-65535, timeout 50-2000 ms, "
                                 "press/gap 20-500 ms.", parent=self.w)
            return
        used = [v.get() for v in self.map_vars.values() if v.get() != "NONE"]
        if len(used) != len(set(used)):
            if not messagebox.askyesno("Duplicate buttons", "Two functions share an Xbox button. Save anyway?",
                                       parent=self.w):
                return
        backend_changed = cfg.backend != self.vars["backend"].get()
        port_changed = cfg.port != port
        with self.rx.lock:
            cfg.backend = self.vars["backend"].get()
            cfg.port, cfg.timeout_ms, cfg.gear_press_ms, cfg.gear_gap_ms = port, timeout, press, gap
            cfg.invert_steering = bool(self.vars["invert_steering"].get())
            cfg.beacon = bool(self.vars["beacon"].get())
            cfg.mapping = {k: v.get() for k, v in self.map_vars.items()}
            cfg.save()
        if backend_changed:
            threading.Thread(target=self.rx.open_backend, daemon=True).start()
        if port_changed and self.rx.running:
            self.rx.stop()
            self.rx.start()
        self.w.destroy()
