JAR := build/libs/burp-vigolium.jar
RELEASE_JAR := burp-vigolium.jar

.PHONY: build

build:
	./gradlew spotlessApply shadowJar
	cp -f $(JAR) $(RELEASE_JAR)
