#!/usr/bin/env python3
"""Materialize legacy template-based movable guitar voicings.

The app keeps hand-curated open and common shapes first. This generator only fills
formula/root pairs for which this file has a reviewed hand template. Newer formula
families are covered by ChordVoicingGenerator at runtime, so adding a formula does
not require inventing a stale hard-coded template here.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORMULAS_PATH = ROOT / "app/src/main/assets/chords/chord_formulas.json"
VOICINGS_PATH = ROOT / "app/src/main/assets/chords/guitar_voicings.json"

ROOTS = ("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
PITCH_CLASS = {root: index for index, root in enumerate(ROOTS)}
PITCH_CLASS.update({"Db": 1, "Eb": 3, "Gb": 6, "Ab": 8, "Bb": 10})
STANDARD_TUNING = (4, 9, 2, 7, 11, 4)  # E2 A2 D3 G3 B3 E4, pitch classes only.
STANDARD_TUNING_MIDI = (40, 45, 50, 55, 59, 64)
NATURAL_INTERVALS = {1: 0, 2: 2, 3: 4, 4: 5, 5: 7, 6: 9, 7: 11}
GENERATED_TAG = "generated-full-coverage"


# Fret offsets are relative to a root on the fifth (A) or sixth (E) string.
# None means a muted string. Finger 0 is reserved for muted/open strings.
# These are closed, transposable shapes; advanced voicings intentionally omit
# only the intervals documented by the generated omittedIntervals field.
TEMPLATES = {
    "maj": ("A", [None, 0, 2, 2, 2, 0], [0, 1, 3, 3, 3, 1]),
    "m": ("A", [None, 0, 2, 2, 1, 0], [0, 1, 3, 4, 2, 1]),
    "7": ("A", [None, 0, 2, 0, 2, 0], [0, 1, 3, 1, 4, 1]),
    "maj7": ("A", [None, 0, 2, 1, 2, 0], [0, 1, 3, 2, 4, 1]),
    "m7": ("A", [None, 0, 2, 0, 1, 0], [0, 1, 3, 1, 2, 1]),
    "mMaj7": ("A", [None, 0, 2, 1, 1, 0], [0, 1, 4, 2, 3, 1]),
    "6": ("A", [None, 0, 2, 2, 2, 2], [0, 1, 3, 3, 3, 3]),
    "m6": ("A", [None, 0, 2, 2, 1, 2], [0, 1, 3, 3, 2, 4]),
    "9": ("A", [None, 0, -1, 0, 0, 0], [0, 2, 1, 3, 3, 3]),
    "maj9": ("A", [None, 0, -1, 1, 0, None], [0, 2, 1, 4, 3, 0]),
    "m9": ("A", [None, 0, -2, 0, 0, 0], [0, 2, 1, 3, 3, 3]),
    "add9": ("A", [None, 0, -1, 2, 0, 0], [0, 2, 1, 4, 3, 3]),
    "sus2": ("A", [None, 0, 2, 2, 0, 0], [0, 1, 3, 4, 1, 1]),
    "sus4": ("A", [None, 0, 2, 2, 3, 0], [0, 1, 2, 3, 4, 1]),
    "dim": ("A", [None, 0, 1, 2, 1, None], [0, 1, 2, 4, 3, 0]),
    "dim7": ("A", [None, 0, 1, -1, 1, None], [0, 2, 3, 1, 4, 0]),
    "m7b5": ("A", [None, 0, 1, 0, 1, None], [0, 1, 2, 1, 3, 0]),
    "aug": ("A", [None, 0, -1, -2, -2, None], [0, 4, 3, 1, 2, 0]),
    "5": ("A", [None, 0, 2, 2, None, None], [0, 1, 3, 4, 0, 0]),
    "7sus4": ("A", [None, 0, 2, 0, 3, None], [0, 1, 2, 1, 4, 0]),
    "7b5": ("A", [None, 0, 1, 0, 2, None], [0, 1, 2, 1, 3, 0]),
    "7#5": ("E", [0, None, 0, 1, 1, None], [1, 0, 2, 3, 4, 0]),
    "7b9": ("A", [None, 0, -1, 0, -1, None], [0, 2, 1, 3, 1, 0]),
    "7#9": ("A", [None, 0, -1, 0, 1, None], [0, 2, 1, 3, 4, 0]),
    "11": ("A", [None, 0, 0, 0, 0, 0], [0, 1, 1, 1, 1, 1]),
    "13": ("A", [None, 0, -1, 0, 0, 2], [0, 2, 1, 3, 3, 4]),
}


def interval_semitones(label: str) -> int:
    number = int("".join(character for character in label if character.isdigit()))
    degree = (number - 1) % 7 + 1
    octaves = (number - 1) // 7
    accidental = label.count("#") - label.lower().count("b")
    return NATURAL_INTERVALS[degree] + octaves * 12 + accidental


def canonical_note(note: str) -> str:
    return ROOTS[PITCH_CLASS[note]]


def canonical_symbol(symbol: str) -> str:
    chord, separator, bass = symbol.partition("/")
    root_length = 2 if len(chord) > 1 and chord[1] in "#b" else 1
    root = canonical_note(chord[:root_length])
    normalized = root + chord[root_length:]
    return normalized + ("/" + canonical_note(bass) if separator else "")


def symbol_for(root: str, formula: dict) -> str:
    return root + formula.get("suffix", "" if formula["id"] == "maj" else formula["id"])


def sounded_pitch_classes(voicing: dict) -> set[int]:
    return {
        (STANDARD_TUNING[index] + fret) % 12
        for index, fret in enumerate(voicing["frets"])
        if fret >= 0
    }


def expected_intervals(voicing: dict, formulas: dict[str, dict]) -> dict[str, int]:
    formula = formulas[voicing["formulaId"]]
    root_pitch = PITCH_CLASS[canonical_note(voicing["root"])]
    return {
        interval: (root_pitch + interval_semitones(interval)) % 12
        for interval in formula["intervals"]
    }


def annotate_omissions(voicing: dict, formulas: dict[str, dict]) -> None:
    actual = sounded_pitch_classes(voicing)
    intervals = expected_intervals(voicing, formulas)
    voicing["omittedIntervals"] = [label for label, pitch in intervals.items() if pitch not in actual]
    positive_frets = [fret for fret in voicing["frets"] if fret > 0]
    if positive_frets:
        start_fret = max(1, voicing.get("startFret", 1))
        voicing["visibleFretCount"] = max(
            voicing.get("visibleFretCount", 4),
            max(positive_frets) - start_fret + 1,
        )


def root_fret(root: str, anchor: str) -> int:
    value = (PITCH_CLASS[root] - PITCH_CLASS[anchor]) % 12
    return value if value > 0 else 12


def contains_barre(frets: list[int], fingers: list[int]) -> bool:
    for finger in range(1, 5):
        positions = [index for index, value in enumerate(fingers) if value == finger and frets[index] > 0]
        for first in positions:
            if any(frets[second] == frets[first] for second in positions if second > first):
                return True
    return False


def generated_id(symbol: str) -> str:
    return "generated-" + symbol.lower().replace("#", "s").replace("/", "-") + "-movable"


def build_generated(root: str, formula: dict) -> dict:
    anchor, offsets, fingers = TEMPLATES[formula["id"]]
    anchor_fret = root_fret(root, anchor)
    minimum_offset = min(offset for offset in offsets if offset is not None)
    while anchor_fret + minimum_offset <= 0:
        anchor_fret += 12
    frets = [-1 if offset is None else anchor_fret + offset for offset in offsets]
    positive = [fret for fret in frets if fret > 0]
    lowest = min(positive)
    highest = max(positive)
    start_fret = 1 if highest <= 5 else lowest
    visible_frets = max(4, highest - start_fret + 1)
    symbol = symbol_for(root, formula)
    anchor_string = "第 5 弦" if anchor == "A" else "第 6 弦"
    result = {
        "id": generated_id(symbol),
        "chordSymbol": symbol,
        "name": f"{symbol} 可移动按法",
        "root": root,
        "formulaId": formula["id"],
        "bassNote": root,
        "frets": frets,
        "fingers": fingers,
        "startFret": start_fret,
        "visibleFretCount": visible_frets,
        "difficulty": formula["difficulty"],
        "isCommon": True,
        "isSimplified": False,
        "hasBarre": contains_barre(frets, fingers),
        "description": f"封闭可移动手型，根音位于{anchor_string}。",
        "tags": ["movable", "full-coverage", GENERATED_TAG, anchor.lower() + "-shape", formula["category"]],
        "omittedIntervals": [],
    }
    annotate_omissions(result, {formula["id"]: formula})
    if result["omittedIntervals"]:
        omitted = "、".join(result["omittedIntervals"])
        result["description"] += f"为保证吉他上的可演奏性，采用常见省略配置：省略 {omitted}。"
    return result


def validate(voicings: list[dict], formulas: dict[str, dict]) -> None:
    ids: set[str] = set()
    symbols: set[str] = set()
    for voicing in voicings:
        identifier = voicing["id"]
        if identifier in ids:
            raise ValueError(f"duplicate voicing id: {identifier}")
        ids.add(identifier)
        if len(voicing["frets"]) != 6 or len(voicing["fingers"]) != 6:
            raise ValueError(f"{identifier} must describe six strings")
        for fret, finger in zip(voicing["frets"], voicing["fingers"]):
            if fret < -1 or fret > 24:
                raise ValueError(f"{identifier} has an invalid fret: {fret}")
            if finger < 0 or finger > 4 or (fret <= 0 and finger != 0) or (fret > 0 and finger == 0):
                raise ValueError(f"{identifier} has an invalid finger assignment")
            if fret > 0 and not voicing["startFret"] <= fret < voicing["startFret"] + voicing["visibleFretCount"]:
                raise ValueError(f"{identifier} has a fret outside its visible fret range")
        actual = sounded_pitch_classes(voicing)
        expected = expected_intervals(voicing, formulas)
        expected_pitches = set(expected.values())
        extra = actual - expected_pitches
        if extra:
            raise ValueError(f"{identifier} sounds non-chord pitch classes: {sorted(extra)}")
        declared = voicing.get("omittedIntervals", [])
        calculated = [label for label, pitch in expected.items() if pitch not in actual]
        if declared != calculated:
            raise ValueError(f"{identifier} omission metadata differs: {declared} != {calculated}")
        if GENERATED_TAG in voicing.get("tags", []):
            root_pitch = PITCH_CLASS[canonical_note(voicing["root"])]
            sounded_midi = [STANDARD_TUNING_MIDI[i] + fret for i, fret in enumerate(voicing["frets"]) if fret >= 0]
            if root_pitch not in actual or min(sounded_midi) % 12 != root_pitch:
                raise ValueError(f"{identifier} must contain the root as its lowest pitch")
        symbols.add(canonical_symbol(voicing["chordSymbol"]))

    missing = [
        symbol_for(root, formula)
        for formula in formulas.values()
        if formula["id"] in TEMPLATES
        for root in ROOTS
        if symbol_for(root, formula) not in symbols
    ]
    if missing:
        raise ValueError("missing formula/root voicings: " + ", ".join(missing))


def main() -> None:
    formula_document = json.loads(FORMULAS_PATH.read_text(encoding="utf-8-sig"))
    voicing_document = json.loads(VOICINGS_PATH.read_text(encoding="utf-8-sig"))
    formulas = {formula["id"]: formula for formula in formula_document["formulas"]}
    if not set(TEMPLATES).issubset(formulas):
        stale_templates = sorted(set(TEMPLATES) - set(formulas))
        raise ValueError(f"templates reference removed formulas: {stale_templates}")

    curated = [
        voicing
        for voicing in voicing_document["voicings"]
        if GENERATED_TAG not in voicing.get("tags", [])
    ]
    for voicing in curated:
        annotate_omissions(voicing, formulas)

    covered = {canonical_symbol(voicing["chordSymbol"]) for voicing in curated}
    generated = []
    for formula in formula_document["formulas"]:
        if formula["id"] not in TEMPLATES:
            continue
        for root in ROOTS:
            symbol = symbol_for(root, formula)
            if symbol not in covered:
                generated.append(build_generated(root, formula))
                covered.add(symbol)

    result = curated + generated
    validate(result, formulas)
    voicing_document["schemaVersion"] = 2
    voicing_document["voicings"] = result
    VOICINGS_PATH.write_text(
        json.dumps(voicing_document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"kept {len(curated)} curated voicings; generated {len(generated)}; total {len(result)}")


if __name__ == "__main__":
    main()
