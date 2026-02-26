S.A.C.O. (SQL Agentic Co-Pilot Orchestrator)
S.A.C.O. is a context-aware, "always-on-top" desktop assistant designed to bridge the gap between AI and Microsoft SQL Server Management Studio (SSMS). It allows developers to scan their active SSMS editor, have an AI review or fix the code, and instantly inject the corrected results back into the IDE.

🚀 The "Context Bridge" Workflow
S.A.C.O. operates in a seamless 4-step automated loop:

🔍 Scan SSMS: Uses the Java Robot class to automate an Alt+Tab, Ctrl+A, and Ctrl+C sequence, physically grabbing code from SSMS without a native plugin.

💬 AI Review: The code is sent to an n8n-hosted AI Agent (powered by Gemini 1.5 Flash) for debugging or optimization.

⬅️ Grab AI Code: Automatically extracts the formatted SQL blocks from the chat history and moves them into the local editor.

⬇️ Apply to SSMS: Injects the fixed code back into the SSMS window using an automated Ctrl+V sequence.

🏗️ System Architecture
1. Frontend: JavaFX Desktop App
   Always-on-Top UI: Built with a SplitPane layout and configured with primaryStage.setAlwaysOnTop(true) to remain visible while working in SSMS.

Modern Workspace: Features a dark-themed code editor for manual tweaks and a conversational side panel for AI interaction.

OS-Level Automation: Utilizes java.awt.Robot to facilitate cross-application data transfer.

2. Backend Orchestration: n8n Workflow

Webhook Trigger: Receives JSON payloads from the Java app containing user prompts and current code context.

AI Agent: A sophisticated node configured with a custom system prompt that distinguishes between "Safe Drafting" (providing code) and "Execution Overrides".

Memory Management: Integrated with a Simple Memory node to maintain conversation context with a sliding window to prevent API rate-limiting.

3. Database Connectivity
   Direct Execution: When explicitly authorized with a SYSTEM_COMMAND_EXECUTE: trigger, the n8n agent can run queries directly against a local Microsoft SQL Server instance and return results in a readable table format.

🛠️ Setup & Requirements
Prerequisites
JDK 21+ (with JavaFX modules).

n8n (running locally via Docker or Desktop).

Google Gemini API Key (configured in n8n).

Microsoft SQL Server Management Studio (SSMS).

Configuration
n8n Setup: Import the provided .json workflow into n8n and set the Webhook path to saco-chat.

Database Tool: Configure the Microsoft SQL Node in n8n with your local database credentials.

App Icon: Place saco-icon.png in the src/main/resources folder to enable the custom taskbar branding.

🛡️ Security & "Safe Draft" Mode
S.A.C.O. follows a Human-in-the-Loop safety philosophy. By default, the AI agent is forbidden from executing queries. It only performs database actions when the Java app prepends the secure execution header, ensuring no accidental UPDATE or DELETE commands are run without a manual review.