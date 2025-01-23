# 🌟 Contribute to refact-intellij & refact-chat-js

## 📚 Table of Contents
- [❤️ Ways to Contribute](#%EF%B8%8F-ways-to-contribute)
  - [🐛 Report Bugs](#-report-bugs)
- [Instructions for React Chat build for JetBrains IDEs (to run locally)](#-instructions-for-react-chat-build-for-jetbrains-ides-to-run-locally)


### ❤️ Ways to Contribute

* Fork the repository
* Create a feature branch
* Do the work
* Create a pull request
* Maintainers will review it


### 🐛 Report Bugs
Encountered an issue? Help us improve Refact.ai by reporting bugs in issue section, make sure you label the issue with correct tag [here](https://github.com/smallcloudai/refact-intellij/issues)! 



### 🔨 Instructions for React Chat build for JetBrains IDEs (to run locally)
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
