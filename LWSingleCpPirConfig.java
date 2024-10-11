package edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.SingleCpPirConfig;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.SingleCpPirFactory.SingleCpPirType;

/**
 * Pai client-specific preprocessing PIR config.
 *
 * @author Jingyu Ning
 * @date 2024/10/11
 */
public class LWSingleCpPirConfig extends AbstractMultiPartyPtoConfig implements SingleCpPirConfig {

    public LWSingleCpPirConfig(Builder builder) {
        super(SecurityModel.MALICIOUS);
    }

    @Override
    public SingleCpPirType getPtoType() {
        return SingleCpPirType.LW;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<LWSingleCpPirConfig> {

        public Builder() {
            // empty
        }

        @Override
        public LWSingleCpPirConfig build() {
            return new LWSingleCpPirConfig(this);
        }
    }
}
