#!/usr/bin/env python3
"""
Generate 100 varied test instances for batch throughput testing.
Each instance follows the same schema but with realistic variation.
"""

import json
import random
import os

random.seed(42)  # reproducible

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# =========================================================================
# JSON Orders — 100 varied instances
# =========================================================================

FIRST_NAMES = ["Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Hank",
               "Iris", "Jack", "Karen", "Leo", "Mona", "Nick", "Olivia", "Paul",
               "Quinn", "Rosa", "Sam", "Tina", "Uma", "Victor", "Wendy", "Xander",
               "Yara", "Zach", "Marcel", "Sophie", "Lars", "Ingrid"]

LAST_NAMES = ["Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Martinez",
              "Davis", "Rodriguez", "Wilson", "Anderson", "Taylor", "Thomas", "Moore",
              "Jackson", "White", "Harris", "Clark", "Lewis", "Walker", "de Vries",
              "Mueller", "Johansson", "Dubois", "Rossi", "Grauwen", "Van Dijk",
              "Nakamura", "Chen", "Patel"]

COUNTRIES = ["NL", "DE", "FR", "UK", "US", "NL", "DE", "NL"]
TIERS = ["Platinum", "Gold", "Silver", "Standard", "Standard", "Silver", "Gold"]

PRODUCTS = [
    ("LAPTOP-15", "ProBook Laptop 15\"", 1299.00, 3.2),
    ("LAPTOP-13", "UltraBook 13\"", 999.00, 1.8),
    ("MONITOR-27", "4K Monitor 27\"", 549.00, 6.5),
    ("MONITOR-32", "Ultra-Wide 32\"", 799.00, 8.2),
    ("MOUSE-WL", "Wireless Mouse", 49.95, 0.15),
    ("KEYBOARD-MECH", "Mechanical Keyboard", 129.00, 0.8),
    ("DOCK-USB", "USB-C Docking Station", 189.00, 0.5),
    ("HEADSET-NC", "Noise-Canceling Headset", 299.00, 0.35),
    ("WEBCAM-4K", "4K Webcam", 149.00, 0.2),
    ("SSD-1TB", "1TB NVMe SSD", 119.00, 0.05),
    ("RAM-32GB", "32GB DDR5 RAM Kit", 159.00, 0.1),
    ("CABLE-HDMI", "HDMI 2.1 Cable 2m", 24.99, 0.1),
    ("STAND-DESK", "Adjustable Desk Stand", 349.00, 12.0),
    ("CHAIR-ERGO", "Ergonomic Office Chair", 699.00, 18.0),
    ("POWER-UPS", "UPS 1500VA", 449.00, 15.0),
]

def generate_json_order(index):
    first = random.choice(FIRST_NAMES)
    last = random.choice(LAST_NAMES)
    country = random.choice(COUNTRIES)
    tier = random.choice(TIERS)

    # Vary item count: 1-8 items per order
    num_items = random.randint(1, 8)
    items = []
    for _ in range(num_items):
        prod = random.choice(PRODUCTS)
        qty = random.randint(1, 10)
        discount = random.choice([0, 0, 0, 0.05, 0.10, 0.15, 0.20])
        items.append({
            "sku": prod[0],
            "productName": prod[1],
            "quantity": qty,
            "unitPrice": prod[2],
            "discount": discount,
            "weight": prod[3]
        })

    credit_limits = [5000, 10000, 15000, 25000, 50000, 100000]

    return {
        "orderId": f"ORD-2026-{10000 + index:05d}",
        "orderDate": f"2026-04-{random.randint(1,28):02d}T{random.randint(8,18):02d}:{random.randint(0,59):02d}:00Z",
        "customer": {
            "firstName": first,
            "lastName": last,
            "email": f"{first.upper()}.{last.upper()}@EXAMPLE.COM",
            "country": country,
            "tier": tier,
            "creditLimit": random.choice(credit_limits)
        },
        "items": items,
        "express": random.choice([True, False, False, False]),
        "fraudScore": round(random.uniform(0.0, 1.0), 3)
    }


# =========================================================================
# XML Invoices — 100 varied instances
# =========================================================================

VENDOR_NAMES = [
    "Mueller und Soehne GmbH", "Tech Solutions Inc.", "Nordic Systems AB",
    "Dubois Consulting SARL", "Smith Engineering Ltd", "Rossi Automation SpA",
    "Van Dijk Electronics BV", "Chen Manufacturing Co.", "Patel Services Ltd",
    "Global Supply Corp", "EuroTech GmbH", "Atlantic Trading Ltd",
    "Pacific Innovations Inc", "Alpine Solutions AG", "Coastal Systems BV"
]

BUYER_DEPARTMENTS = ["Engineering", "Marketing", "Sales", "Finance", "Operations",
                     "IT", "HR", "Procurement", "Research", "Legal"]

INVOICE_ITEMS = [
    ("Cloud Infrastructure Services", "S01"),
    ("Technical Support (hourly)", "S02"),
    ("Software Licenses (per user)", "S03"),
    ("Hardware Maintenance Contract", "S04"),
    ("Data Analytics Platform", "S05"),
    ("Security Audit Services", "S06"),
    ("Network Equipment", "H01"),
    ("Server Hardware", "H02"),
    ("Storage Array Expansion", "H03"),
    ("Backup Solution", "H04"),
    ("Training & Certification", "T01"),
    ("Consulting Services", "T02"),
    ("Project Management", "T03"),
    ("Custom Development", "T04"),
    ("Migration Services", "T05"),
]

PAYMENT_TERMS = ["NET_10", "NET_30", "NET_30", "NET_30", "NET_60"]
CURRENCIES = ["EUR", "EUR", "EUR", "USD", "GBP", "CHF"]

IBANS = [
    "DE89370400440532013000", "NL91ABNA0417164300", "FR7630006000011234567890189",
    "GB29NWBK60161331926819", "CH9300762011623852957", "AT611904300234573201",
    "BE68539007547034", "IT60X0542811101000000123456", "ES9121000418450200051332",
    "SE4550000000058398257466"
]

BICS = ["COBADEFFXXX", "ABNANL2A", "BNPAFRPP", "NWBKGB2L", "UBSWCHZH80A",
        "OPSKATWW", "BPOTBEB1", "UNCRITMM", "CAIXESBB", "ESSESESS"]


def xml_escape(s):
    """Escape special XML characters in text content."""
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;").replace("'", "&apos;")


def generate_xml_invoice(index):
    vendor = random.choice(VENDOR_NAMES)
    buyer = f"Company-{random.randint(100,999)} {random.choice(['GmbH', 'Ltd', 'Inc', 'BV', 'AG', 'SpA'])}"
    dept = random.choice(BUYER_DEPARTMENTS)
    currency = random.choice(CURRENCIES)
    terms = random.choice(PAYMENT_TERMS)
    vat_rate = random.choice([0.19, 0.20, 0.21, 0.08, 0.077])
    iban_idx = random.randint(0, len(IBANS) - 1)

    num_items = random.randint(1, 6)
    items_xml = ""
    for i in range(num_items):
        item = random.choice(INVOICE_ITEMS)
        qty = random.randint(1, 100)
        unit_price = round(random.uniform(50, 5000), 2)
        amount = round(qty * unit_price, 2)
        items_xml += f"""
      <Item>
        <Description>{xml_escape(item[0])}</Description>
        <TaxCode>{item[1]}</TaxCode>
        <Quantity>{qty}</Quantity>
        <Amount>{amount}</Amount>
      </Item>"""

    tax_id = f"{random.choice(['DE','NL','FR','GB','US'])}{random.randint(100000000,999999999)}"
    country = random.choice(["Germany", "Netherlands", "France", "United Kingdom", "United States", "Switzerland"])

    return f"""<?xml version="1.0" encoding="UTF-8"?>
<Invoice>
  <InvoiceNumber>INV-{2026000 + index}</InvoiceNumber>
  <InvoiceDate>2026-04-{random.randint(1,28):02d}</InvoiceDate>
  <Currency>{currency}</Currency>
  <VATRate>{vat_rate}</VATRate>
  <PaymentTerms>{terms}</PaymentTerms>
  <Vendor>
    <Name>{xml_escape(vendor)}</Name>
    <TaxID>{tax_id}</TaxID>
    <Country>{xml_escape(country)}</Country>
  </Vendor>
  <Buyer>
    <Name>{xml_escape(buyer)}</Name>
    <Department>{xml_escape(dept)}</Department>
  </Buyer>
  <LineItems>{items_xml}
  </LineItems>
  <BankDetails>
    <IBAN>{IBANS[iban_idx]}</IBAN>
    <BIC>{BICS[iban_idx]}</BIC>
  </BankDetails>
</Invoice>"""


# =========================================================================
# Generate all instances
# =========================================================================

def main():
    # JSON orders
    json_dir = os.path.join(SCRIPT_DIR, "json-orders", "instances")
    os.makedirs(json_dir, exist_ok=True)
    for i in range(100):
        order = generate_json_order(i + 1)
        with open(os.path.join(json_dir, f"order-{i+1:03d}.json"), "w") as f:
            json.dump(order, f, indent=2)
    print(f"Generated 100 JSON order instances in {json_dir}")

    # XML invoices
    xml_dir = os.path.join(SCRIPT_DIR, "xml-invoices", "instances")
    os.makedirs(xml_dir, exist_ok=True)
    for i in range(100):
        invoice = generate_xml_invoice(i + 1)
        with open(os.path.join(xml_dir, f"invoice-{i+1:03d}.xml"), "w") as f:
            f.write(invoice)
    print(f"Generated 100 XML invoice instances in {xml_dir}")

    # Also generate a combined JSONL file for batch testing
    jsonl_path = os.path.join(SCRIPT_DIR, "json-orders", "all-orders.jsonl")
    with open(jsonl_path, "w") as f:
        for i in range(100):
            order = generate_json_order(i + 1)
            f.write(json.dumps(order) + "\n")
    print(f"Generated JSONL batch file: {jsonl_path}")


if __name__ == "__main__":
    main()
