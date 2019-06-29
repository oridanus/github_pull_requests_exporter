# Github Pull Requests Exporter
Exports your pull requests with their reviewers to a CSV

To Run, you'll need to configure the following params:
```
-DGITHUB_AUTH_KEY=<your Personal access token, no need for the "token" at the begining>
-DGITHUB_REPOS=<comma seperated names of the repos you want to export>
-DGITHUB_OWNER=<github owner>
-DTARGET_EXPORT_FILE_PATH=<the path of the created CSV file>
```

To create a personal access token:
https://help.github.com/en/articles/creating-a-personal-access-token-for-the-command-line
