# Reliable Data Transfer

Reliable Data Transfer is working implementation of two reliable protocols used in computer networks, which are Go-Back-N and Selective Repeat.

## Design ideas/justifications

Go-Back-N Sender:
I have one main loop where I first check whether I can send new packets(depending on sequence number). Then, I wait for receiving new packets(ACKs). I decode the received packets modify my base number and global timer accordingly. And then loop repeats. Every time when I send package for which sequence number equals base I start my global timer with specified timeout. On timeout, I resend all packets in my window and restart my timer. For holding window packets I use hashmap.

Go-Back-N Receiver:
Again, I have one main loop, where I receive packets and increment my expected sequence number accordingly.

Selective Repeat Sender:
I have one main loop where I first check whether I can send new packets(depending on sequence number). Then, I wait for receiving new packets(ACKs). After I decode the received packets, I set the boolean, for packet being acked, to true, meaning that this packet was acked. I check whether the acked packet is the first packet in my window, if so, I modify current base number and also remove acked packets from my window. And then loop repeats. I have one global timer which is started when first packet is sent. Every time when I send package, I record the timestamp + timeout as field for that packet. When I get timeout, I loop through my window and find the packet for which timestamp is less than current time, I set its timestamp to current time + timeout and resend that package. I also find the next candidate for resend, calculate time for its resend and schedule new timer with that timeout. For holding window packets I use hashmap.

Selective Repeat Receiver:
Again, I have one main loop, where I receive packets and increment my expected sequence number accordingly. I also have window for our of order packets, which is also updated appropriately on each received packet.

## How to build?

To build both server and client, run: make all.