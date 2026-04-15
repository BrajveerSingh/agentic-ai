# ATA Architecture Diagrams

This folder contains Mermaid diagram files (`.mmd`) for the Audit Trail Agent architecture.

## How to Import into Draw.io

### Method 1: Direct Import (Recommended)
1. Open [draw.io](https://app.diagrams.net/) or your local draw.io desktop app
2. Go to **Arrange** → **Insert** → **Advanced** → **Mermaid**
3. Copy the content from any `.mmd` file and paste it into the dialog
4. Click **Insert**

### Method 2: Drag and Drop
1. Some versions of draw.io support dragging `.mmd` files directly into the canvas

### Method 3: Via VS Code Extension
1. Install the "Draw.io Integration" extension in VS Code
2. Create a new `.drawio` file
3. Use the Mermaid import feature

## Diagram Files

| File | Description |
|------|-------------|
| `01_high_level_architecture.mmd` | Overall system architecture with all layers |
| `02_deployment_architecture.mmd` | Infrastructure deployment within bank VPC |
| `03_logical_components.mmd` | Spring Boot application component structure |
| `04_agent_core_class_diagram.mmd` | Core agent classes and relationships |
| `05_request_processing_classes.mmd` | Loan processing domain classes |
| `06_audit_event_model.mmd` | Audit event class hierarchy |
| `07_entity_relationship_diagram.mmd` | Database ERD |
| `08_loan_evaluation_sequence.mmd` | Loan evaluation process flow |
| `09_audit_retrieval_sequence.mmd` | Audit trail retrieval flow |
| `10_data_flow.mmd` | Complete data flow through the system |
| `11_security_boundaries.mmd` | Network security zones |

## Tips for Draw.io

- After importing, you can rearrange elements for better visualization
- Use **Format** → **Style** to change colors and themes
- Export as PNG, SVG, or PDF for documentation
- Save as `.drawio` to preserve editability

