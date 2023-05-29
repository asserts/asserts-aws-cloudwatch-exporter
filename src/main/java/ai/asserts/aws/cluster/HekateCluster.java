/*
 *  Copyright © 2020.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.cluster;

import io.hekate.cluster.ClusterNode;
import io.hekate.cluster.ClusterTopology;
import io.hekate.cluster.event.ClusterEvent;
import io.hekate.cluster.event.ClusterEventListener;
import io.hekate.core.HekateException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "deployment.mode", havingValue = "distributed")
public class HekateCluster implements ClusterEventListener {
    private ClusterTopology clusterTopology;

    @Override
    public void onEvent(ClusterEvent event) throws HekateException {
        clusterTopology = event.topology();
    }

    public ClusterNode localNode() {
        return clusterTopology.localNode();
    }

    public List<ClusterNode> allNodes() {
        return clusterTopology.nodes();
    }
}
