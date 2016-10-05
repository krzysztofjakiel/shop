package io.pillopl.eventsource.shop.integration

import io.pillopl.eventsource.shop.domain.events.ItemOrdered
import io.pillopl.eventsource.shop.domain.events.ItemPaid
import io.pillopl.eventsource.shop.domain.events.ItemPaymentTimeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.cloud.stream.messaging.Source
import org.springframework.cloud.stream.test.binder.MessageCollector
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import spock.util.concurrent.PollingConditions

import java.util.concurrent.BlockingQueue

import static io.pillopl.eventsource.shop.CommandFixture.orderItemCommand
import static io.pillopl.eventsource.shop.CommandFixture.markPaymentTimeoutCommand
import static io.pillopl.eventsource.shop.CommandFixture.payItemCommand

class E2ESpec extends IntegrationSpec {

    private UUID anyUuid = UUID.randomUUID()

    @Autowired Source source
    @Autowired Sink commands
    @Autowired MessageCollector eventsCollector

    BlockingQueue<Message<?>> events
    PollingConditions conditions = new PollingConditions(timeout: 12, initialDelay: 0, factor: 1)

    def setup() {
        events = eventsCollector.forChannel(source.output())
    }

    def 'received order command should result in emitted item ordered event'() {
        when:
            commands.input().send(new GenericMessage<Object>(orderItemCommand(anyUuid)))
        then:
            conditions.eventually {
                expectedMessageThatContains(ItemOrdered.TYPE)
            }
    }

    def 'received pay command should result in emitted item paid event'() {
        when:
            commands.input().send(new GenericMessage<Object>(orderItemCommand(anyUuid)))
        and:
            commands.input().send(new GenericMessage<Object>(payItemCommand(anyUuid)))
        then:
            conditions.eventually {
                expectedMessageThatContains(ItemOrdered.TYPE)
            }
        and:
            conditions.eventually {
                expectedMessageThatContains(ItemPaid.TYPE)
            }
    }

    def 'received mark missing payment command should result in emitted marked as missed event'() {
        when:
            commands.input().send(new GenericMessage<Object>(orderItemCommand(anyUuid)))
        and:
            commands.input().send(new GenericMessage<Object>(markPaymentTimeoutCommand(anyUuid)))
        then:
            conditions.eventually {
                expectedMessageThatContains(ItemOrdered.TYPE)
            }
        and:
            conditions.eventually {
                expectedMessageThatContains(ItemPaymentTimeout.TYPE)
            }
    }

    void expectedMessageThatContains(String text) {
        Message<String> msg = events.poll()
        assert msg != null
        assert msg.getPayload().contains(text)
    }

}
