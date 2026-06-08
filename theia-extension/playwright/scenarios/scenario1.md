# Scenario: 1-simple transformation expanded

- **Goal:** simple mapping
- **Mode:** demo 
- **Needs:** Theia :4000 · utlxd · UTLX MCP :7780? (only if an AI-assist step is used)
- **precondition:** Load browser (editor Load uses the native file picker — preserves the file's %utlx header and is Playwright-drivable), Name Keep 
- **Data:** input json -> examples/json/00-enterprise.order

## Steps
1. <action>  → Load a JSON input ("keep" via the new switch) <expected / what the audience should see>
2. <action>  →  Output default json
3.  type a small UTL-X transform in Monaco 
4. execute and show result
5. change output to xml
6. execute and show result
7. change output to yaml
8. execute and show result
9. load utlx examples/json/00-enterprise-order-to-fulfillment-ticket.utlx
10. scroll slowly down the monaco editor to show the complexity
11. execute and show result
12. scroll slowly down in the result
13. Open the function builder (FB) it opens by default in the available Inputs tree (AIT)
14. FB IAT: Open the $input tree in the available Inputs, 
15. FB IAT: click on > in front of $input (>$input) to open the tree in the tree, wait 2 seconds
16. FB IAT: (scroll down), Click on > in front of customer, wait 2 seconds
17. FB IAT: scroll further down) click on > in front of PrimaryContact, wait 2 seconds
18. FB IAT: scroll further down) click on > in front of address, wait 2 seconds
19. FB IAT: scroll further down)click on Postalcode, wait 2 seconds
20. FB IAT: click the insert icon in the postalcode line, wait 4 seconds
21. FB Click in FB to the TAB operators (OP)
22. FB OP: Click Arithmetic (to open), wait 5 seconds
23. FB OP: Click Arithmetic (to close)
24. FB OP: Click Comparison (to open), Wait 5 seconds
25. FB OP: Click Comparison (to close)
26. FB OP: Click Logical (to open), wait 5 seconds
27. FB OP: Click Logical (to close)
28. FB OP:  Click Special (to open), click on each item and wait 3 seconds in between and 3 seconds for the last
29. FB Click TAB Stanard Library (SL), 
30. FB SL: scrol slowly downwards  till YAML is visable and than back upwards to Geospatial
31. FB SL: Click Geospatial (to open), wait 3 seconds
32. FB SL: Click destinationPoint and wait 103 seconds
33. FB SL: UTLX EXPRSSION EDITOR Clean any text visable
34. FB SL: destinationPoint click the insert into. UTLX EXPRSSION EDITOR
35. FB SL: Click Close (for the function Builder)
36. Click Name keep and see that is has changed to name inherit
37. Now add in the input another input by loading the 01-employee-roster.csv, wait 5 seconds
38. Clear the UTLX
39. Create a simple UTLX mapping which uses both $input and $employee-roster (slow place please)
40. execute and show result, wit 5 seconds
41. change output to xml
42. execute and show result, wit 5 seconds
43. delete all additional inputs
44. clear button input
45. clear button output
46. clear button transformation
47. Click Name keep and see that is has changed to name keep
48. Switch to Message Contract Mode
49. inpput instance TAB LOAD examples/mcm/06-order-customers/sample.order.json
50. rename input to order
51. Wait 8 seconds
52. Input order Schema TAB load examples/mcm/06-order-customers/input1.order-schema.json
53. Wait 8 seconds
54. Add additional input and rename to custome
55. input customer instance TAB LOAD examples/mcm/06-order-customers/sample.customer.json
56.  Wait 8 seconds
57.  input customer schema TAB LOAD examples/mcm/06-order-customers/input2.customer.schema.json
58.  wait 8 seconds
59.  output schema TAB LOAD examples/mcm/06-order-customers/output.customs-declaration.schemna.json
60.  wait 8 seconds
61.  click AI assist
62.  wait 2 seconds
63.  click > Contract coverage (so it is opening)
64.  wait 30 seconds
65.  click cancel in AI assist
66.  clear all input,
67.  claer transformaation
68.  clear output
69.  rename input to shipment
70.  input schipment instance TAB LOAD examples/mcm/10-logistics-manifest-xml-csv-json//sample.shipment.xml
71.  wait 8 seconds
72.  input  schipment schema TAB LOAD examples/mcm/10-logistics-manifest-xml-csv-json/input1.shipment.xsd 
73.  wait 8 seconds
74.  Add additional input (2nd) and rename to packages
75.  input packages  instance TAB LOAD examples/mcm/10-logistics-manifest-xml-csv-jsonn/sample.packages.csv
76.   wait 8 seconds
77.  input packages schema TAB LOAD examples/mcm/10-logistics-manifest-xml-csv-json/input2.packages.tsch.json
78.   wait 8 seconds
79.   Add additional input (3rd) and rename to customer
80.   input customer instance TAB LOAD examples/mcm/10-logistics-manifest-xml-csv-json/sample.customer.json
81.   wait 8 seconds
82.   input customer  schmea TAB LOAD examples/mcm/10-logistics-manifest-xml-csv-json/input3.customer.schema.json
83.   wait 8 seconds
84.   output schema TAB LOAD examples/mcm/10-logistics-manifest-xml-csv-json/output.clean.shipping-manifest.xsd
85.   wait 8 seconds
86.   click AI assist
87.   wait 20 seconds
88.   In the prompt type MAP
89.   wait 42 seconds
90.   click > Contract coverage (so it is opening)
91.   wait 20 seonds
92.   click map to output contract
93.   

## Notes
- <pacing / narration cues for a talk>
- <anything fragile or order-dependent>

