FILE=CAN_BUS_tlc.mch
FILE=rule_medium500.mch
test:
	./gradlew run -Pfile="$(FILE)" -Ptypecheck="false"
build/libs/antlr-parser-all-0.1.0-SNAPSHOT.jar: src/main/java/de/prob/parser/antlr/*.java
	./gradlew fatJar
testjar: build/libs/antlr-parser-all-0.1.0-SNAPSHOT.jar
	time java -jar build/libs/antlr-parser-all-0.1.0-SNAPSHOT.jar $(FILE) false

DFILE=~/git_root/prob_examples/public_examples/B/Benchmarks/scheduler
#DFILE=~/git_root/prob_examples/public_examples/B/Benchmarks/Cruise_finite1
DFILE=~/git_root/prob_examples/public_examples/B/Benchmarks/phonebook7
#DFILE=~/git_root/prob_examples/public_examples/B/Benchmarks/CSM
DFILE=~/git_root/prob_examples/public_examples/B/Benchmarks/CarlaTravelAgencyErr
PBFILE=antlr.prob
diff: build/libs/antlr-parser-all-0.1.0-SNAPSHOT.jar
	time java -jar build/libs/antlr-parser-all-0.1.0-SNAPSHOT.jar $(DFILE).mch false >$(PBFILE)
	time java -jar $(PHOME)/lib/probcliparser.jar $(DFILE).mch -prolog -time
	probcli $(PBFILE) -pp_with_name PPDIFF antlr_pp.mch
	probcli $(DFILE).mch -pp_with_name PPDIFF sable_pp.mch
	#diff $(PBFILE) $(DFILE).prob
	diff antlr_pp.mch sable_pp.mch

PHOME=~/git_root/prob_prolog/
testoriginal:
	time java -jar $(PHOME)/lib/probcliparser.jar $(FILE) -prolog -time
