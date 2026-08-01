curl -s https://start.spring.io/starter.tgz \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=4.0.0 \
  -d javaVersion=21 \
  -d groupId=br.com.flaviohblima \
  -d artifactId=lab65-batch-migration \
  -d name=lab65-batch-migration \
  -d dependencies=batch,postgresql \
  | tar -xzvf -
