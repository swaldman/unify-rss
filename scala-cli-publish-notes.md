# scala-cli publish notes

```bash
$ scala-cli --power config publish.credentials oss.sonatype.org env:SONATYPE_USERNAME env:SONATYPE_PASSWORD
$ scala-cli --power publish --gpg-key <current-key-id> .
```

