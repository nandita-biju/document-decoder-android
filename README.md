# Document Decoder

**An LLM-powered Android application that transforms complex documents into actionable information.**

## Overview

Document Decoder is an Android application designed to help users understand documents containing important instructions, deadlines, requirements, and conditions.

The application uses a Large Language Model (LLM) through a Google Agent Development Kit (ADK) agent to analyze document text and extract structured, actionable information. Users can review the extracted results, track tasks, and save analyses for future reference.

## Objectives

- Simplify the interpretation of complex document text.
- Extract actionable tasks, deadlines, requirements, and important conditions.
- Provide supporting text for extracted information.
- Allow users to save and manage extracted tasks.
- Evaluate the reliability of LLM-based information extraction.

## Planned Features

- Text input for document analysis
- AI-powered action and deadline extraction
- Structured analysis results
- Editable task tracking
- Local document history
- Deadline reminders
- Inference evaluation using a labelled test dataset

## Technology Stack

| Component | Technology |
|---|---|
| Mobile application | Android Studio, Java/Kotlin |
| AI agent | Google ADK |
| Language model | Hosted LLM API |
| Backend | Python, Flask |
| Local storage | SQLite |
| Evaluation | Python |

## System Workflow

1. The user enters document text.
2. The Android application sends the text to the backend.
3. The ADK agent uses an LLM to identify actions, deadlines, requirements, and conditions.
4. The backend validates and returns structured results.
5. The application displays the results and allows the user to save and manage tasks.
6. Model outputs are evaluated against manually labelled sample documents.

## Project Status

**Status:** Initial development and planning.

Features and technologies described above are planned and may change during implementation.

## Future Enhancements

- PDF text extraction
- Source-grounded question answering
- Improved handling of long documents
- Comparison of different prompting strategies
- Evaluation of extraction accuracy, latency, and token usage

## Disclaimer

AI-generated information may contain errors or omit important details. Users should verify extracted information against the original document, particularly when dealing with deadlines, financial obligations, or official requirements.
