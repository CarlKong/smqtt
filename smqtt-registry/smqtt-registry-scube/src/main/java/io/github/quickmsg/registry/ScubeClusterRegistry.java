package io.github.quickmsg.registry;

import io.github.quickmsg.common.cluster.ClusterConfig;
import io.github.quickmsg.common.cluster.ClusterMessage;
import io.github.quickmsg.common.cluster.ClusterNode;
import io.github.quickmsg.common.cluster.ClusterRegistry;
import io.github.quickmsg.common.enums.ClusterEvent;
import io.scalecube.cluster.Cluster;
import io.scalecube.cluster.ClusterImpl;
import io.scalecube.cluster.ClusterMessageHandler;
import io.scalecube.cluster.Member;
import io.scalecube.cluster.membership.MembershipEvent;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.net.Address;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author luxurong
 */
public class ScubeClusterRegistry implements ClusterRegistry {

    private Sinks.Many<ClusterMessage> messageMany = Sinks.many().multicast().onBackpressureBuffer();

    private Sinks.Many<ClusterEvent> eventMany = Sinks.many().multicast().onBackpressureBuffer();

    private Cluster cluster;


    @Override
    public void registry(ClusterConfig clusterConfig) {
        cluster = new ClusterImpl()
                .config(opts -> opts.memberAlias(clusterConfig.getNodeName()))
                .membership(opts -> opts.seedMembers(clusterConfig
                        .getClusterUrl()
                        .stream()
                        .map(Address::from)
                        .collect(Collectors.toList())))
                .handler(cluster -> new ClusterHandler())
                .startAwait();

    }

    @Override
    public Flux<ClusterMessage> handlerClusterMessage() {
        return messageMany.asFlux();
    }

    @Override
    public List<ClusterNode> getClusterNode() {
        return null;
    }

    @Override
    public Mono<Void> spreadMessage(ClusterMessage clusterMessage) {
        return Optional.ofNullable(cluster)
                .map(cs -> cs.spreadGossip(Message.withData(clusterMessage).build()).then()).orElse(Mono.empty());
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> Optional.ofNullable(cluster)
                .ifPresent(Cluster::shutdown));
    }

    @Override
    public Flux<ClusterEvent> clusterEvent() {
        return eventMany.asFlux();
    }


    class ClusterHandler implements ClusterMessageHandler {

        @Override
        public void onMessage(Message message) {
            messageMany.tryEmitNext(new ClusterMessage());
        }

        @Override
        public void onGossip(Message gossip) {
            messageMany.tryEmitNext(new ClusterMessage());

        }

        @Override
        public void onMembershipEvent(MembershipEvent event) {
            Member member = event.member();
            eventMany.tryEmitNext(null);
        }
    }
}