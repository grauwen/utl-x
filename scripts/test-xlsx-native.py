#!/usr/bin/env python3
"""Exercise XLSX input and output through a built native CLI, using only the Python standard library."""

import argparse
from datetime import date
import io
import json
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile


SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"


def write_fixture(path):
    """Create independent Excel XML with shared strings, a date, a cached formula and a hidden sheet."""
    excel_date = (date(2026, 11, 21) - date(1899, 12, 30)).days
    parts = {
        "[Content_Types].xml": """
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet2.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
              <Override PartName="/xl/sharedStrings.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
            </Types>
        """,
        "_rels/.rels": """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Target="xl/workbook.xml"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"/>
            </Relationships>
        """,
        "xl/workbook.xml": f"""
            <workbook xmlns="{SPREADSHEET_NS}"
              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Hidden" sheetId="1" state="hidden" r:id="rId1"/>
                <sheet name="Collection" sheetId="2" r:id="rId2"/>
              </sheets>
            </workbook>
        """,
        "xl/_rels/workbook.xml.rels": """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Target="worksheets/sheet1.xml"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
              <Relationship Id="rId2" Target="worksheets/sheet2.xml"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
              <Relationship Id="rId3" Target="styles.xml"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"/>
              <Relationship Id="rId4" Target="sharedStrings.xml"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings"/>
            </Relationships>
        """,
        "xl/styles.xml": f"""
            <styleSheet xmlns="{SPREADSHEET_NS}">
              <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
              <fills count="2"><fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill></fills>
              <borders count="1"><border/></borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
              <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>
        """,
        "xl/sharedStrings.xml": f"""
            <sst xmlns="{SPREADSHEET_NS}" count="1" uniqueCount="1"><si><t>Réserve 🥃</t></si></sst>
        """,
        "xl/worksheets/sheet1.xml": f"""
            <worksheet xmlns="{SPREADSHEET_NS}"><sheetData>
              <row r="1"><c r="A1" t="inlineStr"><is><t>Ignored</t></is></c></row>
            </sheetData></worksheet>
        """,
        "xl/worksheets/sheet2.xml": f"""
            <worksheet xmlns="{SPREADSHEET_NS}"><sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t>Name</t></is></c>
                <c r="B1" t="inlineStr"><is><t>Purchased</t></is></c>
                <c r="C1" t="inlineStr"><is><t>Remaining</t></is></c>
                <c r="D1" t="inlineStr"><is><t>Approximate</t></is></c>
                <c r="E1" t="inlineStr"><is><t>Unknown</t></is></c>
              </row>
              <row r="2"><c r="A2" t="s"><v>0</v></c><c r="B2" s="1"><v>{excel_date}</v></c>
                <c r="C2"><f>350*2</f><v>700</v></c><c r="D2" t="b"><v>0</v></c></row>
              <row r="3"/>
            </sheetData></worksheet>
        """,
    }
    with ZipFile(path, "w", ZIP_DEFLATED) as workbook:
        for name, content in parts.items():
            workbook.writestr(name, content.strip().encode("utf-8"))


def verify(binary, rows, jar=None, agent_directory=None):
    with tempfile.TemporaryDirectory(prefix="utlx-native-xlsx-") as temporary:
        directory = Path(temporary)
        spool = directory / "spool"
        spool.mkdir()

        def run(*arguments, data=None, success=True, error=None):
            command = [str(binary), f"-Djava.io.tmpdir={spool}"]
            if agent_directory is not None:
                command.append(f"-agentlib:native-image-agent=config-merge-dir={agent_directory}")
            if jar is not None:
                command.extend(["-jar", str(jar)])
            result = subprocess.run(
                [*command, *map(str, arguments), "--no-capture"],
                input=data, capture_output=True, timeout=120,
            )
            if (result.returncode == 0) != success:
                raise AssertionError(result.stderr.decode("utf-8", errors="replace"))
            if error is not None:
                diagnostic = result.stderr.decode("utf-8", errors="replace")
                assert error in diagnostic, f"Expected {error!r} in error: {diagnostic}"
            return result

        def read_json(*arguments, data=None):
            output = directory / "result.json"
            run(*arguments, "-o", output, data=data)
            return json.loads(output.read_text(encoding="utf-8"))

        source = directory / "rows.json"
        workbook = directory / "collection.xlsx"
        records = [
            {"Name": f"Réserve 🥃 {index}", "Remaining": 0, "Approximate": False,
             "Unknown": None, "Literal": "=1+1", "ID": 12345678901234567890}
            for index in range(rows)
        ]
        records[-1]["Note"] = "Last bottle"
        source.write_text(json.dumps(records, ensure_ascii=False), encoding="utf-8")
        run("convert", "-i", source, "--to", "xlsx", "-o", workbook)
        recovered = read_json("convert", "-i", workbook, "--to", "json")
        expected = [dict(record, ID=str(record["ID"]), Note=record.get("Note")) for record in records]
        assert recovered == expected, "Export/import changed cell values or flushed rows."
        with ZipFile(workbook) as archive:
            sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
            assert not sheet.findall(f".//{{{SPREADSHEET_NS}}}f"), "Literal text became an Excel formula."
            cells = {cell.attrib["r"]: cell for cell in sheet.findall(f".//{{{SPREADSHEET_NS}}}c")}
            assert cells["B2"].attrib.get("t", "n") == "n", "Zero must remain numeric."
            assert cells["C2"].attrib.get("t") == "b", "False must remain boolean."
        print(f"PASS: {rows} records, Unicode, precision, nulls, typed cells and disk-flushed rows")

        fixture = directory / "fixture.xlsx"
        write_fixture(fixture)
        preview = directory / "preview.utlx"
        preview.write_text(
            '%utlx 1.0\ninput xlsx {sheet: "Collection", rowNumbers: true}\noutput json\n---\n$input',
            encoding="utf-8",
        )
        expected_fixture = [{"Name": "Réserve 🥃", "Purchased": "2026-11-21", "Remaining": 700,
                             "Approximate": False, "Unknown": None, "_rowNumber": 2}]
        assert read_json("transform", preview, fixture) == expected_fixture
        assert read_json("transform", preview, "--from", "xlsx", data=fixture.read_bytes()) == expected_fixture
        automatic = read_json("convert", "-i", fixture, "--to", "json")
        assert automatic == [{key: value for key, value in expected_fixture[0].items() if key != "_rowNumber"}]
        print("PASS: selected and visible sheets, shared strings, dates, cached formulas and binary stdin")

        output = run("convert", "-i", source, "--to", "xlsx", "--verbose")
        with ZipFile(io.BytesIO(output.stdout)) as archive:
            assert archive.testzip() is None
        print("PASS: binary stdout remains an intact XLSX archive with verbose diagnostics")

        empty = directory / "empty.json"
        empty.write_text("[]", encoding="utf-8")
        writer = directory / "writer.utlx"
        writer.write_text(
            '%utlx 1.0\ninput json\noutput xlsx {sheet: "Export", columns: ["Name", "Remaining"]}\n---\n$input',
            encoding="utf-8",
        )
        run("transform", writer, empty, "-o", workbook)
        with ZipFile(workbook) as archive:
            definition = ET.fromstring(archive.read("xl/workbook.xml"))
            assert definition.find(f"{{{SPREADSHEET_NS}}}sheets")[0].attrib["name"] == "Export"
        assert read_json("convert", "-i", workbook, "--to", "json") == []
        print("PASS: explicit sheet/columns and empty exports")

        previous = workbook.read_bytes()
        source.write_text('[{"Nested":[]}]', encoding="utf-8")
        run("convert", "-i", source, "--to", "xlsx", "-o", workbook, success=False, error="scalar")
        assert workbook.read_bytes() == previous, "Failed export replaced the previous workbook."
        source.write_text(json.dumps([{"ID": 1}] * 10001), encoding="utf-8")
        run("convert", "-i", source, "--to", "xlsx", success=False, error="10000")
        run("convert", "--from", "xlsx", "--to", "json", data=b"invalid workbook", success=False, error="zip")
        preview.write_text(preview.read_text(encoding="utf-8").replace("Collection", "Missing"), encoding="utf-8")
        run("transform", preview, fixture, success=False, error="not found")
        columns = json.dumps([f"Column{index}" for index in range(257)])
        writer.write_text(
            f"%utlx 1.0\ninput json\noutput xlsx {{columns: {columns}}}\n---\n$input", encoding="utf-8"
        )
        run("transform", writer, empty, success=False, error="256")
        assert not [path for path in spool.rglob("*") if path.is_file()], "Workbook temporary files leaked."
        print("PASS: row/column limits, invalid files/sheets, nested cells, safe replacement and temporary cleanup")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path, help="Path to the built native utlx executable")
    parser.add_argument("--rows", type=int, default=250, help="Export/import size (101 through 10000)")
    parser.add_argument("--jar", type=Path, help="Compare on the JVM: pass java as binary and the CLI JAR here")
    parser.add_argument("--agent-directory", type=Path, help="Collect native-image metadata during a JVM comparison")
    arguments = parser.parse_args()
    if not 101 <= arguments.rows <= 10000:
        parser.error("--rows must be between 101 and 10000")
    jar = arguments.jar.resolve(strict=True) if arguments.jar is not None else None
    agent_directory = arguments.agent_directory
    if agent_directory is not None:
        if jar is None:
            parser.error("--agent-directory requires --jar and a GraalVM java executable")
        agent_directory = agent_directory.resolve()
        agent_directory.mkdir(parents=True, exist_ok=True)
    verify(arguments.binary.resolve(strict=True), arguments.rows, jar, agent_directory)
    print("All native XLSX checks passed.")


if __name__ == "__main__":
    main()
