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

## Notes
- <pacing / narration cues for a talk>
- <anything fragile or order-dependent>

