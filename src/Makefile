JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
        GBNReceiver.java \
        GBNSender.java \
        SRReceiver.java \
        SRSender.java \
        Channel.java \
        LogUtils.java \
        Packet.java \

default: classes

all: classes

classes: $(CLASSES:.java=.class)
	 chmod +x gbnReceiver
	 chmod +x gbnSender
	 chmod +x srReceiver
	 chmod +x srSender

clean:
	$(RM) *.class 