# scala-cli publish notes

```bash
$ scala-cli --power config publish.credentials central env:SONATYPE_USER env:SONATYPE_PASSWORD
$ scala-cli --power config publish.credentials oss.sonatype.org env:SONATYPE_USER env:SONATYPE_PASSWORD
$ export SONATYPE_USER=$SONATYPE_USERNAME
$ scala-cli --power publish --gpg-key <current-key-id> .
```
