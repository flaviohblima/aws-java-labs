curl -s https://start.spring.io/starter.tgz \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.3.5 \
  -d javaVersion=21 \
  -d groupId=br.com.flaviohblima \
  -d artifactId=lab5-beanstalk \
  -d name=lab5-beanstalk \
  -d dependencies=web,actuator \
  | tar -xzvf -