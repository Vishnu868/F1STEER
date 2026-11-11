"""PyInstaller entry point (a package's __main__ cannot be frozen directly)."""
from f1wheel.__main__ import main

raise SystemExit(main())
