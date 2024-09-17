# refact-intellij & refact-chat-js
### Instructions for React Chat build for JetBrains IDEs (to run locally)
1. Clone the branch alpha of the repository `refact-chat-js`.
2. Install dependencies and build the project:
   ```bash
   npm install && npm run build
   ```
3. Clone the branch `dev` of the repository `refact-intellij`.
4. Move the generated `dist` directory from the `refact-chat-js` repository to the `src/main/resources/webview` directory of the `refact-intellij` repository.
5. Wait for the files to be indexed.
6. Open the IDE and navigate to the Gradle panel, then select Run Configurations with the suffix [runIde].
7. In the Environment variables field, insert `REFACT_DEBUG=1`.
8. Start the project by right-clicking on the command `refact-intellij [runIde]`.
9. Inside the Refact.ai settings in the new IDE (PyCharm will open), select the field `Secret API Key` and press the key combination `Ctrl + Alt + - (minus)`, if using MacOS: `Command + Option + - (minus)`.
10. Scroll down and insert the port value for `xDebug LSP port`, which is the port under which LSP is running locally. By default, LSP's port is `8001`.
11. After that, you can test the chat functionality with latest features.