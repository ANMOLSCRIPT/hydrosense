"""Load .env from the repository root for the helper scripts."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

try:
    from dotenv import load_dotenv

    for name in (".env", ".env.local"):
        load_dotenv(ROOT / name)
except ImportError:  # pragma: no cover
    pass
