#!/usr/bin/env bash
cd ../
mvn package




#Hacky copy the jar to the assemblies of linkedcache importer project
TARGETS=("../linkedcacheimporter/vertexapi/lib/" "../linkedcacheimporter/vertexsource/lib/"  "../linkedcacheimporter/linkedcacheimport/lib/")

for target in "${TARGETS[@]}"
do 
	cp target/websocketclient-1.0.jar $target
	cp target/websocketclient-1.0-sources.jar $target
done

