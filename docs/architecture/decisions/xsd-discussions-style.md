There is a classification of XML Schema (XSD) design styles, often referred to as XSD design patterns. These patterns describe different ways to structure an XSD based on how elements and types are declared—either globally or locally. While tools like XMLSpy, Tibco BusinessWorks, Stylus Studio, and others may generate different XSDs from the same metadata, they typically follow one or more of these recognized patterns.
🧩 The Four Main XSD Design Patterns

These patterns are widely acknowledged in XML schema design literature and practice:


Russian Doll

All elements and types are declared locally.
Simple and compact.
Harder to reuse components.
Often used in small or tightly scoped schemas.



Salami Slice

All elements are declared globally.
Promotes reuse of elements.
Can lead to namespace complexity.
Common in large-scale integration scenarios.



Venetian Blind

Elements are declared locally, types are declared globally.
Balances reuse and encapsulation.
Often preferred for modular schema design.
Supported well by tools like XMLSpy and Stylus Studio.



Garden of Eden

All elements and types are declared globally.
Maximizes reuse.
Can be complex to manage.
Often used in enterprise-level schemas.



These patterns are based on two binary choices:

Are element declarations global or local?
Are type definitions global or local?

Each pattern has trade-offs in terms of reusability, encapsulation, complexity, and tool compatibility. For example, Tibco BusinessWorks tends to favor Salami Slice or Garden of Eden styles, using ref attributes extensively to reference global elements. [oracle.com], [balisage.net]

🛠️ Tool-Specific Behavior

Different tools may prefer or default to different patterns when generating XSDs from metadata:

XMLSpy: Offers options to choose the pattern (Russian Doll, Salami Slice, Venetian Blind) when generating schemas from XML instances or databases. [altova.com]

Tibco BusinessWorks: Often uses ref attributes, aligning with Salami Slice or Garden of Eden patterns.

Stylus Studio: Allows manual control over schema structure and supports multiple patterns.

FreeFormatter.com: Lets users choose between Russian Doll, Salami Slice, and Venetian Blind when generating XSDs from XML. [freeformatter.com]


🧠 Expressive Power and Transformability
Interestingly, research shows that:

Venetian Blind is the most expressive and flexible pattern.
Salami Slice and Garden of Eden are equivalent in expressive power.
Russian Doll is less reusable and harder to transform into other patterns without loss of structure. [balisage.net]
