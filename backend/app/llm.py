"""Optional LLM enhancement for assessment explanations.

Only active when ANTHROPIC_API_KEY is configured. The model receives the
structured evidence produced by the deterministic engine and rewrites the
explanation in plain language. It cannot change the assessment, the confidence
or any number. Any failure falls back silently to the rule-based text.
"""
from __future__ import annotations

import hashlib
import json
import logging
import os

log = logging.getLogger("hydrosense.llm")

SYSTEM = (
    "You write short explanations for HydroSense, a citizen water-monitoring prototype. "
    "You are given structured evidence as JSON. Write 2-3 plain sentences a member of the public "
    "can understand, explaining what the evidence shows.\n"
    "Rules: use only facts present in the JSON; never invent or estimate measurements; "
    "TDS is one indicator and cannot establish pollution, ecosystem health or drinking-water safety, "
    "so describe changes as potential and never as confirmed pollution; no advice about drinking or "
    "swimming; no markdown, no lists, no preamble. Reply with the explanation text only."
)

_cache: dict[str, str] = {}


def enabled() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY"))


def evidence_key(evidence: dict, risk: str) -> str:
    blob = json.dumps({"e": evidence, "r": risk}, sort_keys=True, default=str)
    return hashlib.sha256(blob.encode()).hexdigest()[:24]


def explain(evidence: dict, assessment_label: str, risk: str) -> str | None:
    """Return an LLM-written explanation, or None to keep the rule-based one."""
    if not enabled():
        return None
    key = evidence_key(evidence, risk)
    if key in _cache:
        return _cache[key]
    try:
        import anthropic

        client = anthropic.Anthropic(timeout=12.0, max_retries=0)
        response = client.messages.create(
            model=os.environ.get("LLM_MODEL", "claude-opus-5-5"),
            max_tokens=2000,
            output_config={"effort": "low"},
            system=SYSTEM,
            messages=[{
                "role": "user",
                "content": f"Assessment: {assessment_label}\nEvidence:\n{json.dumps(evidence, indent=2, default=str)}",
            }],
        )
        if response.stop_reason == "refusal":
            return None
        text = " ".join(b.text for b in response.content if b.type == "text").strip()
        if not text or len(text) > 900:
            return None
        _cache[key] = text
        return text
    except Exception as exc:  # network, auth, rate limit, SDK missing: keep the rule-based text
        log.warning("LLM explanation unavailable: %s", exc)
        return None
