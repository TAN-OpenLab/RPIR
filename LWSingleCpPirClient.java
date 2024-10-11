package edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.prp.Prp;
import edu.alibaba.mpc4j.common.tool.crypto.prp.PrpFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prp.PrpFactory.PrpType;
import edu.alibaba.mpc4j.common.tool.crypto.stream.StreamCipher;
import edu.alibaba.mpc4j.common.tool.crypto.stream.StreamCipherFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.crypto.matrix.database.ZlDatabase;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.AbstractSingleCpPirClient;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW.LWSingleCpPirConfig;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW.LWSingleCpPirDesc;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW.LWSingleCpPirDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW.LWSingleCpPirUtils;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.openjdk.jol.info.GraphLayout;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.cbrt;
import static java.lang.Math.sqrt;

/**
 * LW client-specific preprocessing PIR client.
 *
 * @author Jingyu Ning
 * @date 2024/10/11
 */
public class LWSingleCpPirClient extends AbstractSingleCpPirClient {
//    //ning-a

    /**
     * block size
     */
    private int bufferNum = 1 << 20;
    /**
     * w,论文中的w
     */
    private int w;
    /**
     * recursive level, 论文中的r,递归层数
     */
    private int r;

    /**
     * permutation keys
     */
    private int dataKey = 13;

    private int dataBias = 7;



    /**
     * ek:对称加密密钥
     */
    private byte[] clientKey;
    /**
     * s：PRF密钥
     */
    private byte[] clientKey2;

    private byte[] iv ;

    public final PrpType type = PrpType.NATIVE_AES;


    public boolean isPrime(int n) {
        // 排除0、1和负数
        if (n < 2) {
            return false;
        }
        // 取平方根提高效率
        int sqrt_n = (int) Math.sqrt(n);
        // 从2到平方根遍历是否有因子
        for (int i = 2; i <= sqrt_n; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        // 无因子，是素数
        return true;
    }
//    public static byte[] intToByteArray(int i) {
//        byte[] result = new byte[4];
//        result[0] = (byte)((i >> 24) & 0xFF);
//        result[1] = (byte)((i >> 16) & 0xFF);
//        result[2] = (byte)((i >> 8) & 0xFF);
//        result[3] = (byte)(i & 0xFF);
//        return result;
//    }
//
//    public static int getInt(byte[] b, int offset) {
//        int n = 0;
//        int len = b.length;
//        if (len >= offset + 4) {
//            // 低字节在前
//            int byte0 = b[offset] & 0xff; // 最右边的字节，不需要移位
//            int byte1 = b[offset + 1] & 0xff;// 右边第二个字节，需要左移一个字节，8位
//            int byte2 = b[offset + 2] & 0xff;// 右边第三个字节，需要左移两个字节，16位
//            int byte3 = b[offset + 3] & 0xff;// 最左边的字节，需要左移三哥字节，24位
//            n = byte3 | byte2 << 8 | byte1 << 16 | byte0 << 24;
//        }
//        return n;
//    }

    //ning-b

    /**
     * stream cipher
     */
    private final StreamCipher streamCipher;
    /**
     * row num
     */
    private int rowNum;
    /**
     * column num
     */
    private int columnNum;

    /**
     * local cache entries
     */
    private TIntObjectMap<byte[]> localCacheEntries;

    public LWSingleCpPirClient(Rpc clientRpc, Party serverParty, LWSingleCpPirConfig config) {
        super(LWSingleCpPirDesc.getInstance(), clientRpc, serverParty, config);
        streamCipher = StreamCipherFactory.createInstance(envType);
    }

    @Override
    public void init(int n, int l) throws MpcAbortException {
        setInitInput(n, l);
        logPhaseInfo(PtoState.INIT_BEGIN);

        //ning-a
        while(!isPrime(bufferNum)){
            bufferNum = bufferNum + 1;
        }
        int product = bufferNum,rowPow,columnPow;
        r=1;
        while (product < n) { //计算r
            r=r+1;
            product = product *bufferNum;

        }
        w = (int) Math.ceil((double)n / Math.pow(bufferNum,r-1));
        System.out.print("bufferNum is: " + bufferNum +"\n");
        System.out.print("w  is: " + w  +"\n");


//ning2内存
        preprocessingning();

        logPhaseInfo(PtoState.INIT_END);
    }

    private void prepClient (byte[] s, int mk, int mb,int len, int r) throws MpcAbortException {
        // stream receiving rows
        DataPacketHeader dataRequestHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ROW_STREAM_DATABASE_REQUEST.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> dataRequestPayload = rpc.receive(dataRequestHeader).getPayload();
 //       System.out.print("client receive 1" +"\n");

        MpcAbortPreconditions.checkArgument(dataRequestPayload.size() == 1);
        byte[] dataByteArray = dataRequestPayload.get(0);
        MpcAbortPreconditions.checkArgument(dataByteArray.length == byteL * len);
        // split rows
        ByteBuffer dataByteBuffer = ByteBuffer.wrap(dataByteArray);
        byte[][] dataArray = new byte[len][byteL];
        byte[][] dataArrayShuffled = new byte[len][CommonConstants.BLOCK_BYTE_LENGTH + byteL];
        //从dataByteBuffer读到dataArray
        for (int x = 0; x < len; x++) {
            dataByteBuffer.get(dataArray[x]);
//              System.out.print("client-dataArray["+x+"] = " + Arrays.toString(dataArray[x]) +"\n");

        }

        //重新排序及加密

        int newx;
        for (int x = 0; x < len; x++) {
            newx = (x*mk+mb)%len;
            dataArrayShuffled[newx] = streamCipher.encrypt(clientKey, iv, dataArray[x]);
            //System.out.print("加密后的dataArrayShuffled["+ newx+ "] = " + Arrays.toString(dataArrayShuffled[newx]) +"\n");
            // System.out.print("pai-client-vk= " + Arrays.toString(clientKey) +"\n");
            // byte[] abc = streamCipher.ivDecrypt(clientKey, dataArrayShuffled[newx]);
            //System.out.print("abc["+ newx+ "] = " + Arrays.toString(abc) +"\n");
        }


        ByteBuffer finalByteBuffer = ByteBuffer.allocate((byteL) * len);
        for (int x= 0; x < len; x++) {
//            System.out.print("x="+ x+ " " + "\n");
//            System.out.print("finalByteBuffer.position()="+ finalByteBuffer.position()+ " " + "\n");
//            System.out.print("dataArrayShuffled["+ x+ "] = " + Arrays.toString(dataArrayShuffled[x]) +"\n");
            finalByteBuffer.put(dataArrayShuffled[x]);
        }
        List<byte[]> finalResponsePayload = Collections.singletonList(finalByteBuffer.array());
        DataPacketHeader finalResponseHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_FINAL_STREAM_DATABASE_RESPONSE.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(finalResponseHeader, finalResponsePayload));
        //System.out.print("client send 2" +"\n");
        extraInfo++;

//        //ning 总存储
//        System.out.println("lw1 dataArray size is "+ GraphLayout.parseInstance(dataArray).totalSize());
//        System.out.println("lw1 dataArrayShuffled size is "+ GraphLayout.parseInstance(dataArrayShuffled).totalSize());
//        //ning
//
//        //ning1内存
//        Runtime runtime1 = Runtime.getRuntime();
//        long totalMemory1 = runtime1.totalMemory();
//        long freeMemory1 = runtime1.freeMemory();
//        long usedMemory1 = totalMemory1 - freeMemory1;
//
//        System.out.println("Total Memory1: " + totalMemory1 + " bytes");
//        System.out.println("Free Memory: " + freeMemory1 + " bytes");
//        System.out.println("Used Memory: " + usedMemory1 + " bytes");
//        System.out.println("Used Memory: " + usedMemory1/1024/1024+ "M bytes");
////ning2内存
    }

    /**
     * 在2层递归中，对每一行进行预处理
     */
    private void twoRoundprep (byte[] s, int mk, int mb,int columnNum) throws MpcAbortException {
        DataPacketHeader rowRequestHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), LWSingleCpPirDesc.PtoStep.SERVER_SEND_ROW_STREAM_DATABASE_REQUEST.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> rowRequestPayload = rpc.receive(rowRequestHeader).getPayload();
       // System.out.print("client receive  database"  +"extraInfo is "+extraInfo+"\n");
        MpcAbortPreconditions.checkArgument(rowRequestPayload.size() == 1);
        byte[] rowDataByteArray = rowRequestPayload.get(0);
//        System.out.print("server: rowDataByteArray.length"  +rowDataByteArray.length+"\n");
//        System.out.print("byteL * columnNum"  +byteL * columnNum+"\n");
        MpcAbortPreconditions.checkArgument(rowDataByteArray.length == byteL * columnNum);
        // split rows
        ByteBuffer rowByteBuffer = ByteBuffer.wrap(rowDataByteArray);
        byte[][] rowValueArray = new byte[columnNum][byteL];

        byte[][] dataArrayShuffled = new byte[columnNum][byteL];
        //读数据到rowValueArray
        for (int iColumn = 0; iColumn < columnNum; iColumn++) {
            rowByteBuffer.get(rowValueArray[iColumn]);
        }
        IntStream iColumnIndexStream = IntStream.range(0, columnNum);
        iColumnIndexStream = parallel ? iColumnIndexStream.parallel() : iColumnIndexStream;

        iColumnIndexStream.forEach(iColumn -> {
            //置换
            int newx;  //新位置
            newx = (iColumn * mk + mb) % columnNum;


            dataArrayShuffled[newx] = streamCipher.encrypt(clientKey, iv, rowValueArray[iColumn]);

//                   System.out.print("rowValueArray["+ iColumn+ "] = " + Arrays.toString(rowValueArray[iColumn]) +"\n");
//                    System.out.print("dataArrayShuffled["+ newx+ "] = " + Arrays.toString(dataArrayShuffled[newx]) +"\n");
//                         System.out.print("pai-client-vk= " + Arrays.toString(clientKey) +"\n");
//                         byte[] abc = streamCipher.ivDecrypt(clientKey, dataArrayShuffled[newx]);
//                        System.out.print("abc["+ newx+ "] = " + Arrays.toString(abc) +"\n");

        });
        ByteBuffer finalByteBuffer = ByteBuffer.allocate((byteL) * columnNum);
        for (int x= 0; x < columnNum; x++) {
            //System.out.print("x="+ x+ " " + "\n");
            //System.out.print("dataArrayShuffled["+ x+ "] = " + Arrays.toString(dataArrayShuffled[x]) +"\n");
            finalByteBuffer.put(dataArrayShuffled[x]);
        }
        List<byte[]> finalResponsePayload = Collections.singletonList(finalByteBuffer.array());
        DataPacketHeader finalResponseHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_FINAL_STREAM_DATABASE_RESPONSE.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(finalResponseHeader, finalResponsePayload));
        //System.out.print("client send 对行加密的结果." +"extraInfo is " +extraInfo+"\n");
        extraInfo++;
//        //ning 总存储
//        System.out.println("lw2 dataArray size is "+ GraphLayout.parseInstance(rowValueArray).totalSize());
//        System.out.println("lw2 dataArrayShuffled size is "+ GraphLayout.parseInstance(dataArrayShuffled).totalSize());
//        //ning
////
//        //ning1内存
//        Runtime runtime1 = Runtime.getRuntime();
//        long totalMemory1 = runtime1.totalMemory();
//        long freeMemory1 = runtime1.freeMemory();
//        long usedMemory1 = totalMemory1 - freeMemory1;
//
//        System.out.println("Total Memory1: " + totalMemory1 + " bytes");
//        System.out.println("Free Memory: " + freeMemory1 + " bytes");
//        System.out.println("Used Memory: " + usedMemory1 + " bytes");
//        System.out.println("Used Memory: " + usedMemory1/1024/1024+ "M bytes");
////ning2内存

    }

    int fr;//r=2时，if fr=0,w; if fr=1, bufferNum
    private void rRoundprep (int r, byte[] s, int mk, int mb) throws MpcAbortException{
        if(r==1){
            prepClient (s, mk, mb,bufferNum, 1);
         //   System.out.print("r==1结束" +"\n");
        }
        else if(r==2){
//            stopWatch.start();

            if (fr==0){
                for (int iRow = 0; iRow < bufferNum; iRow++) {
                    twoRoundprep (clientKey2, dataKey, dataBias+iRow,bufferNum);

                }
                for (int iRow = 0; iRow < bufferNum; iRow++) {
                    twoRoundprep (clientKey2, dataKey, dataBias+iRow,bufferNum);

                }

            }
            else {
                // 第一轮预处理
                for (int iRow = 0; iRow < w; iRow++) {
                    twoRoundprep (clientKey2, dataKey, dataBias+iRow,bufferNum);

                }

//            System.out.print("r==2 第一轮处理结束" +"\n");
//            stopWatch.stop();
//            long rowTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//            stopWatch.reset();
//            logStepInfo(PtoState.INIT_STEP, 2, 3, rowTime, "Client handles " + w + " rows");
//
//            stopWatch.start();
                // 重排之后的每行进行预处理
                for (int iRow = 0; iRow < w; iRow++) {
                    twoRoundprep (clientKey2, dataKey, dataBias+iRow,bufferNum);

                }

            }

       //     System.out.print("r==2 第2轮处理结束" +"\n");
//            stopWatch.stop();
//            long columnime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//            stopWatch.reset();
//            logStepInfo(PtoState.INIT_STEP, 2, 3, columnime, "Client handles " + rowNum + " rows");
        }
        else {
            System.out.print("r==3预处理" +"\n");
            int rz = (int) Math.floor((double)r / 2);

            int z = (int) Math.pow(bufferNum,rz);
            int rh = r-rz;
            int h =(int)  Math.pow(bufferNum,r-rz-1)*w;
            System.out.print("z is: " + z + "h is " + h +"\n");
            System.out.print("rz is: " + rz + "rh is " + rh +"\n");
            for (int iRow = 0; iRow < h; iRow++) {
//                byte[] a = intToByteArray(mk+iRow);
//                byte[] b = streamCipher.encrypt(clientKey2, iv, a);
//                int c = getInt(b,  0);
//                int prfres = (c %bufferNum+bufferNum)%bufferNum;
//                if(prfres==0){
//                    prfres =1;
//                }
//                System.out.print("a=" + Arrays.toString(a) + "r轮第一轮"+"\n");
//                System.out.print("b=" + Arrays.toString(b) + "  c ="+c+"  prfres ="+prfres+" r轮第一轮"+"\n");
//                System.out.print("c%bufferNum=" + c%bufferNum + "  c ="+c+"  prfres ="+prfres+" r轮第一轮"+"\n");
//                rRoundprep (rz, s, prfres, mb+iRow);
//                System.out.print("r=" +r+"   r轮第一轮"+"\n");
//                System.out.print("iRow=" +iRow+"   r轮第一轮"+"\n");

                fr=0;
                rRoundprep (rz, s, mk, mb);
            }
            for (int iColumn = 0; iColumn < z; iColumn++) {
//                byte[] a = intToByteArray(mk+iColumn);
//                byte[] b = streamCipher.encrypt(clientKey2, iv, a);
//                int c = getInt(b,  0);
//                int prfres2 = (c %bufferNum+bufferNum)%bufferNum;
//                if(prfres2==0){
//                    prfres2 =1;
//                }
//                rRoundprep (rh, s, prfres2, mb+iColumn);
//
//                System.out.print("r=" +r+"r轮第二轮"+"\n");
                fr=1;
                rRoundprep (rh, s, mk, mb);
            }
        }


    }

    private void preprocessingning() throws MpcAbortException {
        clientKey = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        secureRandom.nextBytes(clientKey);
        clientKey2 = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        secureRandom.nextBytes(clientKey2);
        iv = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        secureRandom.nextBytes(iv);

        localCacheEntries = new TIntObjectHashMap<>();

        stopWatch.start();
        rRoundprep (r, clientKey2, dataKey, dataBias);

        stopWatch.stop();
        long rowTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 2, 3, rowTime, "Client  " + r + " 轮预处理的总时间");

        System.out.print("rRoundprep结束" +"\n");


    }

    @Override
    public byte[] pir(int x) throws MpcAbortException {
        System.out.print("client before set x = "+x+"\n");
        setPtoInput(x);
        System.out.print("client after set x = "+x+"\n");
        if (localCacheEntries.containsKey(x)) {
            System.out.print("client if 1 x = "+x+"\n");
            return requestLocalQuery(x);
        } else {
            System.out.print("client if 2 x = "+x+"\n");
            return requestActualQuery(x);
        }
    }

    private byte[] requestLocalQuery(int x) throws MpcAbortException {
        // when client asks a query with x in cache, we make a dummy query, otherwise we would also leak information.
        if (localCacheEntries.size() == n) {
            // if all indexes have been queried, request an empty query and return value in local cache
            requestEmptyQuery();
        } else {
            // query a random index that is not queried before
            if (localCacheEntries.size() * 2 <= n) {
                // when queried size is not that large, sample a random query
                boolean success = false;
                int dummyX = -1;
                while (!success) {
                    dummyX = secureRandom.nextInt(n);
                    success = !localCacheEntries.containsKey(dummyX);
                }
                requestActualQuery(dummyX);
            } else {
                // when queries size reaches n / 2, it means we have O(n) storages, sample from the remaining set
                TIntSet remainIndexSet = new TIntHashSet(n);
                remainIndexSet.addAll(IntStream.range(0, n).toArray());
                remainIndexSet.removeAll(localCacheEntries.keys());
                int[] remainIndexArray = remainIndexSet.toArray();
                assert remainIndexArray.length > 0;
                requestActualQuery(remainIndexArray[0]);
            }
        }
        return localCacheEntries.get(x);
    }

    private void requestEmptyQuery() throws MpcAbortException {
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        DataPacketHeader queryRequestHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_QUERY.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(queryRequestHeader, new LinkedList<>()));
        stopWatch.stop();
        long queryTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, queryTime, "Client requests empty query");

        DataPacketHeader queryResponseHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_RESPONSE.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> queryResponsePayload = rpc.receive(queryResponseHeader).getPayload();

        stopWatch.start();
        MpcAbortPreconditions.checkArgument(queryResponsePayload.size() == 0);
        stopWatch.stop();
        long responseTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, responseTime, "Client handles empty response");

        logPhaseInfo(PtoState.PTO_END);
    }

    private int rperm(int r, int x,int mk,int mb){
        int newx;
        if(r==1){
            newx = (x*mk+mb)%bufferNum;
            return newx;
        }
        else if(r==2){

            int u, realRow,v,vp,xp,a,b,bp;
            columnNum =bufferNum;
            if (fr == 0) {
                rowNum=bufferNum;
                System.out.print("client: rowNum=bufferNum "+"\n");
            }
            else{
                rowNum=w;
                System.out.print("client: rowNum=w "+"\n");
            }

            u = x/columnNum;
            v=x%columnNum;
            System.out.print("r==2  u is: " + u + "v is " + v +"\n");
            vp=(v*mk+mb+u)%columnNum;
            xp = vp*rowNum +u;
            System.out.print("r==2 vp is: " + vp + "xp is " + xp +"\n");
            a=xp/columnNum;
            b=xp%columnNum;
            System.out.print("r==2  a is: " + a + "b is " + b +"\n");
            bp=(b*mk+mb+a)%columnNum;

            newx = a*columnNum+bp;
            System.out.print("r==2  bp is: " + bp + "newx is " + newx +"\n");
            return newx;
        }
        else {
            System.out.print("r==3置换" +"\n");
            int rz = (int) Math.floor((double)r / 2);

            int z = (int) Math.pow(bufferNum,rz);
            int rh = r-rz;
            int h =(int)  Math.pow(bufferNum,r-rz-1)*w;
            System.out.print("z is: " + z + "h is " + h +"\n");
            System.out.print("rz is: " + rz + "rh is " + rh +"\n");
            int u=(int) Math.floor((double)x / z);
            int v = x % z;
            System.out.print("u is: " + u + "v is " + v +"\n");
//            byte[] a = intToByteArray(mk+u);
//            byte[] b = streamCipher.encrypt(clientKey2, iv, a);
//            int c = getInt(b,  0);
//            int prfres = (c %bufferNum+bufferNum)%bufferNum;
//            if(prfres==0){
//                prfres =1;
//            }
//            int vp=rperm(rz, v,prfres,mb+u);
            fr=0;
            int vp=rperm(rz, v,mk,mb);

//            byte[] a2 = intToByteArray(mk+vp);
//            byte[] b2= streamCipher.encrypt(clientKey2, iv, a2);
//            int c2 = getInt(b2,  0);
//            int prfres2 = (c2 %bufferNum+bufferNum)%bufferNum;
//            if(prfres2==0){
//                prfres2 =1;
//            }
//            int up=rperm(rh, u,prfres2,mb+vp);
            fr=1;
            int up=rperm(rh, u,mk,mb);
            System.out.print("r==3 up is: " + up + "vp is " + vp +"\n");
            //newx = vp*z+up;
            newx = up*z+vp;
            return newx;
        }

    }
    //查询
    private byte[] requestActualQuery(int x) throws MpcAbortException {
        logPhaseInfo(PtoState.PTO_BEGIN);
        //ning1
        System.out.print("1014client x = "+x+"\n");
        byte[] value = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        stopWatch.start();
        System.out.print("1014client r = "+r+"\n");
        int newx = rperm(r,x,dataKey,dataBias);


        byte[] xBytes = ByteBuffer.allocate(4)
            .putInt(0, newx)
            .array();
        System.out.print("client1024 newx = "+newx+"client-xBytes= " + Arrays.toString(xBytes) +"\n");


        List<byte[]> queryRequestPayload = Collections.singletonList(xBytes);
        DataPacketHeader queryRequestHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_QUERY.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(queryRequestHeader, queryRequestPayload));
        System.out.print("LW-client-quary-send 1 "  +"\n");
        stopWatch.stop();
        long queryTime = stopWatch.getTime(TimeUnit.NANOSECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, queryTime, "Client requests query in NANOSECONDS");

        DataPacketHeader queryResponseHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_RESPONSE.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> queryResponsePayload = rpc.receive(queryResponseHeader).getPayload();

        stopWatch.start();
        MpcAbortPreconditions.checkArgument(queryResponsePayload.size() == 1);
        byte[] responseByteArray = queryResponsePayload.get(0);
        MpcAbortPreconditions.checkArgument(responseByteArray.length == byteL);
        // decrypt
        System.out.print("查询  responseByteArray is "+ "= " + Arrays.toString(responseByteArray )+"\n");
        value = streamCipher.decrypt(clientKey, iv, responseByteArray);
        for(int i=1;i<r;i++){
            value = streamCipher.decrypt(clientKey, iv, value);
        }


        //ning
//            System.out.print("LW-client-responseByteArray= " + Arrays.toString(responseByteArray) +"\n");
//            System.out.print("LW-client-vk= " + Arrays.toString(clientKey) +"\n");
//            System.out.print("LW-client-value= " + Arrays.toString(value) +"\n");
        // add x to the local cache
        localCacheEntries.put(x, value);
        stopWatch.stop();
        long responseTime = stopWatch.getTime(TimeUnit.NANOSECONDS);
        long responseTime2 = stopWatch.getTime(TimeUnit.NANOSECONDS);
        stopWatch.reset();
        //ning2

//        stopWatch.start();
//        // PRP x two times
//        byte[] keyBytes = ByteBuffer.allocate(CommonConstants.BLOCK_BYTE_LENGTH)
//            .putInt(CommonConstants.BLOCK_BYTE_LENGTH - Integer.BYTES, x)
//            .array();
//        System.out.print("pai-client-keyBytes= " + Arrays.toString(keyBytes) +"\n");
//        byte[] medKey = medPrp.prp(keyBytes);
//        byte[] finalKey = finalPrp.prp(medKey);
//        List<byte[]> queryRequestPayload = Collections.singletonList(finalKey);
//        DataPacketHeader queryRequestHeader = new DataPacketHeader(
//            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_QUERY.ordinal(), extraInfo,
//            rpc.ownParty().getPartyId(), otherParty().getPartyId()
//        );
//        rpc.send(DataPacket.fromByteArrayList(queryRequestHeader, queryRequestPayload));
//        stopWatch.stop();
//        long queryTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//        stopWatch.reset();
//        logStepInfo(PtoState.PTO_STEP, 1, 2, queryTime, "Client requests query");
//
//        DataPacketHeader queryResponseHeader = new DataPacketHeader(
//            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_RESPONSE.ordinal(), extraInfo,
//            otherParty().getPartyId(), rpc.ownParty().getPartyId()
//        );
//        List<byte[]> queryResponsePayload = rpc.receive(queryResponseHeader).getPayload();
//
//        stopWatch.start();
//        MpcAbortPreconditions.checkArgument(queryResponsePayload.size() == 1);
//        byte[] responseByteArray = queryResponsePayload.get(0);
//        MpcAbortPreconditions.checkArgument(responseByteArray.length == CommonConstants.BLOCK_BYTE_LENGTH + byteL);
//        // decrypt
//        byte[] value = streamCipher.ivDecrypt(vk, responseByteArray);
//        //ning
//        System.out.print("pai-client-responseByteArray= " + Arrays.toString(responseByteArray) +"\n");
//        System.out.print("pai-client-value= " + Arrays.toString(value) +"\n");
//        // add x to the local cache
//        localCacheEntries.put(x, value);
//        stopWatch.stop();
//        long responseTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, responseTime, "Client handles response");
        logStepInfo(PtoState.PTO_STEP, 2, 2, responseTime2, "Client handles response in NANOSECONDS");

        logPhaseInfo(PtoState.PTO_END);
        return value;
    }


}
