package edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.crypto.matrix.database.ZlDatabase;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.AbstractSingleCpPirServer;
import edu.alibaba.mpc4j.s2pc.pir.cppir.index.LW.LWSingleCpPirDesc.PtoStep; //ning
//import edu.alibaba.mpc4j.s2pc.pir.cppir.index.pai.PaiSingleCpPirDesc;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;


import java.lang.Object;//ning
import java.util.Arrays;
import static java.lang.Math.cbrt;
import static java.lang.Math.sqrt;//ning

/**
 * LW client-specific preprocessing PIR server.
 *
 * @author Jingyu Ning
 * @date 2024/10/11
 */
public class LWSingleCpPirServer extends AbstractSingleCpPirServer {
    //ning-a

    private int dataNum;

    private int dataKey;

    private int dataBias;

    /**
     * padding database
     */
    private ZlDatabase paddingDatabasening;

    byte[][] finaldatabase;

    private int bufferNum = 1 << 20;
    /**
     * 论文中的w
     */
    private int w;
    /**
     * 论文中的r,轮数
     */
    private int r;
    //ning-b

    /**
     * row num
     */
    private int rowNum;
    /**
     * column num
     */
    private int columnNum;
    /**
     * padding database
     */
    private ZlDatabase paddingDatabase;
    /**
     * final database
     */
    private Map<ByteBuffer, byte[]> finalDatabase;

    public LWSingleCpPirServer(Rpc serverRpc, Party clientParty, LWSingleCpPirConfig config) {
        super(LWSingleCpPirDesc.getInstance(), serverRpc, clientParty, config);
    }


    public boolean isPrime(int n) {
        // 排除0、1和负数
        if (n < 2) {
            return false;
        }
        // 取平方根提高效率
        int sqrt_n = (int) sqrt(n);
        // 从2到平方根遍历是否有因子
        for (int i = 2; i <= sqrt_n; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        // 无因子，是素数
        return true;
    }

    public static int getInt(byte[] b, int offset) {
        int n = 0;
        int len = b.length;
        if (len >= offset + 4) {
            // 低字节在前
            int byte0 = b[offset] & 0xff; // 最右边的字节，不需要移位
            int byte1 = b[offset + 1] & 0xff;// 右边第二个字节，需要左移一个字节，8位
            int byte2 = b[offset + 2] & 0xff;// 右边第三个字节，需要左移两个字节，16位
            int byte3 = b[offset + 3] & 0xff;// 最左边的字节，需要左移三哥字节，24位
            n = byte3 | byte2 << 8 | byte1 << 16 | byte0 << 24;
        }
        return n;
    }



    @Override
    public void init(ZlDatabase database) throws MpcAbortException {
        setInitInput(database);
        logPhaseInfo(PtoState.INIT_BEGIN);   //ning-info("{}{} {} Init begin",
        //ning-a
        stopWatch.start();
        while(!isPrime(bufferNum)){
            bufferNum = bufferNum + 1;
        }
        int product = bufferNum;
        r=1;
        while (product < n) { //计算r
            r=r+1;
            product = product *bufferNum;

        }
        if(r==1){
            dataNum =bufferNum;
            w=1;
        }
        else {
            w = (int) Math.ceil((double)n / Math.pow(bufferNum,r-1));
            dataNum = (int)Math.pow(bufferNum,r-1)*w;

        }

        System.out.print("r is: " + r + "  w is " + w +"\n");
        System.out.print("dataNum is: " + dataNum + "  ByteL is " + byteL +"\n");
        // pad the database
        byte[][] paddingDataning = new byte[dataNum][byteL];
        byte aa;
        for (int x = 0; x < n; x++) {
            paddingDataning[x] = database.getBytesData(x);
           // System.out.print("xpaddingDataning["+ x +"]= " + Arrays.toString(paddingDataning[x] )+"\n");


        }
        for (int x = n; x < dataNum; x++) {
            paddingDataning[x] = BytesUtils.randomByteArray(byteL, l, secureRandom);
           // System.out.print("2 xpaddingDataning["+ x +"]= " + Arrays.toString(paddingDataning[x] )+"\n");
        }
        paddingDatabasening = ZlDatabase.create(l, paddingDataning);

//        for (int x = 0; x < dataNum; x++) {
//            System.out.print("paddingDataning[x] = " + Arrays.toString(paddingDataning[x]) +"\n");
//        }

        stopWatch.stop();
        long paddingTimening = stopWatch.getTime(TimeUnit.MILLISECONDS);
        //long paddingTimening2 = stopWatch.getTime(TimeUnit.MICROSECONDS);
        stopWatch.reset();
        logStepInfo(  //ning-info("{}{} {} init Step {}/{} ({}ms): {}",
            PtoState.INIT_STEP, 1, 3, paddingTimening,
            String.format(
                "ning Server sets params: n = %d, dataNum = %d",
                n, dataNum
            )
        );


        preprocessingning();

        //ning-b

//
        logPhaseInfo(PtoState.INIT_END); //LW_CPPIR P_2 Init end
    }

    int fr;//r=2时，if fr=0,w; if fr=1, bufferNum
    private ByteBuffer rprep(int r, ByteBuffer dataByteBuffer, int len) throws MpcAbortException{
        if(r==1){
            List<byte[]> dataRequestPayload = Collections.singletonList(dataByteBuffer.array());

            DataPacketHeader dataRequestHeader = new DataPacketHeader(
                encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ROW_STREAM_DATABASE_REQUEST.ordinal(), extraInfo,
                rpc.ownParty().getPartyId(), otherParty().getPartyId()
            );
            rpc.send(DataPacket.fromByteArrayList(dataRequestHeader, dataRequestPayload));
 //           System.out.print("server send 1" +"extraInfo is"+extraInfo+"\n");

            // receive response
            DataPacketHeader dataResponseHeader = new DataPacketHeader(
                encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_FINAL_STREAM_DATABASE_RESPONSE.ordinal(), extraInfo,
                otherParty().getPartyId(), rpc.ownParty().getPartyId()
            );
            List<byte[]> dataResponsePayload = rpc.receive(dataResponseHeader).getPayload();
            //System.out.print("server receive 2" +"\n");

            MpcAbortPreconditions.checkArgument(dataResponsePayload.size() == 1);
            byte[] dataByteArray = dataResponsePayload.get(0);
            // each med contains encrypted key +(random IV + encrypted value)
            MpcAbortPreconditions.checkArgument(
                dataByteArray.length == (byteL) * len
            );

            // split the stream database
            ByteBuffer dataByteBuffer2 = ByteBuffer.wrap(dataByteArray);

//            //test1
//            byte[][] a= new byte[5][byteL];
//            dataByteBuffer2.get(a[0]);
//            System.out.println("server:a[0] is"+Arrays.toString(a[0]));
//             //test2
            extraInfo++;
            return dataByteBuffer2;

        }
        else if(r==2){
            byte[][] dataArrayN = new byte[len][byteL];
            byte[][] firstEncResult = new byte[len][byteL];
            byte[][] secondEncResult = new byte[len][byteL];
            //finaldatabase = new byte[len][ byteL];[
//            System.out.println("server:len is"+len);
//            System.out.println("server:byteL is"+byteL);
            dataByteBuffer.position(0);
           // System.out.println("position: " + dataByteBuffer.position());
            for (int x = 0; x < len; x++) {
                //System.out.println("server:x is"+x);

                dataByteBuffer.get(dataArrayN[x]);

            }
            if (fr==0){
                for (int iRow = 0; iRow < bufferNum; iRow++) {
                    // concatenate each column into a whole column
                    ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * bufferNum);
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        rowByteBuffer.put(dataArrayN[iRow * bufferNum + iColumn]);
                    }

                    ByteBuffer medByteBuffer = rprep(1, rowByteBuffer,bufferNum);
                    medByteBuffer.position(0);
                    //    System.out.println("position: " + medByteBuffer.position());
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        medByteBuffer.get(firstEncResult[bufferNum*iColumn+iRow]);
                        //System.out.print("firstEncResult["+ (iRow*bufferNum+iColumn) + "] = " + Arrays.toString(firstEncResult[iRow*bufferNum+iColumn]) +"\n");
                    }

                    //extraInfo++;
                }
                for (int iRow = 0; iRow < bufferNum; iRow++) {
                    // concatenate each column into a whole column
                    ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * bufferNum);
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        rowByteBuffer.put(firstEncResult[iRow * bufferNum + iColumn]);
                    }

                    ByteBuffer medByteBuffer = rprep(1, rowByteBuffer,bufferNum);
                    medByteBuffer.position(0);
                    //  System.out.println("position: " + medByteBuffer.position());
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        medByteBuffer.get(secondEncResult[iRow * bufferNum + iColumn]);
                        //  System.out.print("r==2 secondEncResult["+ (iRow * bufferNum + iColumn) + "] = " + Arrays.toString(secondEncResult[iRow * bufferNum + iColumn]) +"\n");
                    }
                    //extraInfo++;
                }
            }
            else {
                for (int iRow = 0; iRow < w; iRow++) {
                    // concatenate each column into a whole column
                    ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * bufferNum);
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        rowByteBuffer.put(dataArrayN[iRow * bufferNum + iColumn]);
                    }

                    ByteBuffer medByteBuffer = rprep(1, rowByteBuffer,bufferNum);
                    medByteBuffer.position(0);
                    //    System.out.println("position: " + medByteBuffer.position());
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        medByteBuffer.get(firstEncResult[w*iColumn+iRow]);
                        //System.out.print("firstEncResult["+ (iRow*bufferNum+iColumn) + "] = " + Arrays.toString(firstEncResult[iRow*bufferNum+iColumn]) +"\n");
                    }

                    //extraInfo++;
                }
                for (int iRow = 0; iRow < w; iRow++) {
                    // concatenate each column into a whole column
                    ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * bufferNum);
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        rowByteBuffer.put(firstEncResult[iRow * bufferNum + iColumn]);
                    }

                    ByteBuffer medByteBuffer = rprep(1, rowByteBuffer,bufferNum);
                    medByteBuffer.position(0);
                    //  System.out.println("position: " + medByteBuffer.position());
                    for (int iColumn = 0; iColumn < bufferNum; iColumn++) {
                        medByteBuffer.get(secondEncResult[iRow * bufferNum + iColumn]);
                        //  System.out.print("r==2 secondEncResult["+ (iRow * bufferNum + iColumn) + "] = " + Arrays.toString(secondEncResult[iRow * bufferNum + iColumn]) +"\n");
                    }
                    //extraInfo++;
                }
            }

            ByteBuffer dataByteBuffer2 = ByteBuffer.allocate(byteL * len);
            dataByteBuffer2.position(0);
            for (int x = 0; x < len; x++) {
                dataByteBuffer2.put(secondEncResult[x]);
            }
            //extraInfo++;
            return dataByteBuffer2;

        }
        else{

            byte[][] dataArrayN = new byte[len][byteL];
            byte[][] firstEncResult = new byte[len][byteL];
            byte[][] secondEncResult = new byte[len][byteL];
            //finaldatabase = new byte[len][ byteL];[
//            System.out.println("r3 server:len is"+len);
//            System.out.println("r3 server:byteL is"+byteL);
            dataByteBuffer.position(0);
         //   System.out.println("position: " + dataByteBuffer.position());
            for (int x = 0; x < len; x++) {
                //System.out.println("server:x is"+x);

                dataByteBuffer.get(dataArrayN[x]);

            }


            System.out.print("r==3" +"\n");
            int rz = (int) Math.floor((double)r / 2);

            int z = (int) Math.pow(bufferNum,rz);
            int rh = r-rz;
            int h =(int)  Math.pow(bufferNum,r-rz-1)*w;
//            System.out.print("z is: " + z + "h is " + h +"\n");
//            System.out.print("rz is: " + rz + "rh is " + rh +"\n");

            //第一轮
            for (int iRow = 0; iRow < h; iRow++) {
                ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * z);
                for (int iColumn = 0; iColumn < z; iColumn++) {
                    rowByteBuffer.put(dataArrayN[iRow * z + iColumn]);
                  //  System.out.print("r==3 round one dataArrayN[ " + iRow * z + iColumn + "] is " + Arrays.toString(dataArrayN[iRow * z + iColumn]) +"\n");
                }

                fr=0;
                ByteBuffer medByteBuffer = rprep(rz, rowByteBuffer,z);
                medByteBuffer.position(0);
             //   System.out.println("position: " + medByteBuffer.position());
                for (int iColumn = 0; iColumn < z; iColumn++) {
                    medByteBuffer.get(firstEncResult[iRow * z + iColumn]);
                 //   System.out.print("firstEncResult["+ (iRow*bufferNum+iColumn) + "] = " + Arrays.toString(firstEncResult[iRow*bufferNum+iColumn]) +"\n");
                }


            }
            System.out.print("r3 r=" +r+"r轮第一轮"+"\n");
            //第二轮
            for (int iColumn = 0; iColumn < z; iColumn++) {
               // rprep (rh, s,h);
                ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * h);
                for (int iRow = 0; iRow < h; iRow++) {
                    rowByteBuffer.put(firstEncResult[iRow * z + iColumn]);
                 //   System.out.print("firstEncResult["+ (iRow * z + iColumn) + "] = " + Arrays.toString(firstEncResult[iRow * z + iColumn]) +"\n");
                }

                fr=1;
                ByteBuffer medByteBuffer = rprep(rh, rowByteBuffer,h);
                medByteBuffer.position(0);
             //   System.out.println("position: " + medByteBuffer.position());
                for (int iRow = 0; iRow < h; iRow++) {
                    medByteBuffer.get(secondEncResult[iRow * z + iColumn]);
                  //  System.out.println("iColumn: " + iColumn+"iRow: " + iRow+"\n");
                //    System.out.print("r==3 secondEncResult["+ (iRow * z + iColumn) + "] = " + Arrays.toString(secondEncResult[iRow * z + iColumn]) +"\n");
                }
                //System.out.print("r=" +r+"r轮第二轮"+"\n");
            }
            ByteBuffer dataByteBuffer2 = ByteBuffer.allocate(byteL * len);
            dataByteBuffer2.position(0);
            for (int x = 0; x < len; x++) {
                dataByteBuffer2.put(secondEncResult[x]);
            }
            //extraInfo++;
            return dataByteBuffer2;


        }


    }
    private void preprocessingning() throws MpcAbortException {

        stopWatch.start();

        ByteBuffer dataByteBuffer = ByteBuffer.allocate(byteL * dataNum);
        for (int x = 0; x < dataNum; x++) {
            dataByteBuffer.put(paddingDatabasening.getBytesData(x));
        }


        // split the stream database
        ByteBuffer dataByteBuffer2 = rprep(r, dataByteBuffer, dataNum);
        byte[][] dataArrayN = new byte[dataNum][ byteL];
        finaldatabase = new byte[dataNum][ byteL];
        dataByteBuffer2.position(0);
     //   System.out.println("position: " + dataByteBuffer2.position());
        for (int x = 0; x < dataNum; x++) {
            dataByteBuffer2.get(dataArrayN[x]);
            finaldatabase[x] = dataArrayN[x];
         //   System.out.print("finaldatabase["+x+"]="+Arrays.toString(finaldatabase[x])+"\n");
        }
       // extraInfo++;
        stopWatch.stop();
        long streamRowTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 2, 2, streamRowTime, "ning-Server handles " + rowNum + " rows");//ning-↓PAI_CPPIR P_1 init Step 2/3 (12ms): Server handles 1 rows


//        if (bufferNum  >= n){
//            stopWatch.start();
//
//            ByteBuffer dataByteBuffer = ByteBuffer.allocate(byteL * dataNum);
//            for (int x = 0; x < dataNum; x++) {
//                dataByteBuffer.put(paddingDatabasening.getBytesData(x));
//            }
//
//
//            // split the stream database
//            ByteBuffer dataByteBuffer2 = rprep(1, dataByteBuffer, dataNum);
//            byte[][] dataArrayN = new byte[dataNum][ byteL];
//            finaldatabase = new byte[dataNum][ byteL];
//            for (int x = 0; x < dataNum; x++) {
//                // read encrypted key
////            byte[] finalKey = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
////            dataByteBuffer2.get(finalKey);
////            finaldatabase[x]=finalKey;
//                dataByteBuffer2.get(dataArrayN[x]);
//                //System.out.print("server-dataArrayN["+ x+ "] = " + Arrays.toString(dataArrayN[x]) +"\n");
//
//                finaldatabase[x] = dataArrayN[x];
//            }
//            extraInfo++;
//            stopWatch.stop();
//            long streamRowTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//            stopWatch.reset();
//            logStepInfo(PtoState.INIT_STEP, 2, 2, streamRowTime, "ning-Server handles " + rowNum + " rows");//ning-↓PAI_CPPIR P_1 init Step 2/3 (12ms): Server handles 1 rows
//        }
//        else if (bufferNum<n && bufferNum >= sqrt(n)){
//            stopWatch.start();
//
//            byte[][] firstEncResult = new byte[rowNum*columnNum][byteL];
//            // stream handling rows
//            for (int iRow = 0; iRow < rowNum; iRow++) {
//                // concatenate each column into a whole column
//                ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * columnNum);
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//                    rowByteBuffer.put(paddingDatabasening.getBytesData(iRow * columnNum + iColumn));
//                }
//
//                ByteBuffer medByteBuffer = rprep(1, rowByteBuffer,columnNum);
//
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//                    medByteBuffer.get(firstEncResult[rowNum*iColumn+iRow]);
//                    System.out.print("firstEncResult["+ (iRow*columnNum+iColumn) + "] = " + Arrays.toString(firstEncResult[iRow*columnNum+iColumn]) +"\n");
//                }
//                extraInfo++;
//            }
//            stopWatch.stop();
//            long streamRowTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//            stopWatch.reset();
//            logStepInfo(PtoState.INIT_STEP, 2, 3, streamRowTime, "ning Server first handles " + rowNum + " rows");//ning-↓PAI_CPPIR P_1 init Step 2/3 (12ms): Server handles 1 rows
//
//            stopWatch.start();
//            finaldatabase = new byte[dataNum][ byteL];
//            //byte[][][] secondEncResult = new byte[rowNum][columnNum][CommonConstants.BLOCK_BYTE_LENGTH + byteL];
//            // stream handling columns
//            for (int iRow = 0; iRow < rowNum; iRow++) {
//                // concatenate each column into a whole column
//                ByteBuffer rowByteBuffer = ByteBuffer.allocate((byteL) * columnNum);
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//                    rowByteBuffer.put(firstEncResult[iRow * columnNum + iColumn]);
//                    System.out.print("1server firstEncResult["+ (iColumn*rowNum+iRow) + "] = " + Arrays.toString(firstEncResult[iColumn*rowNum+iRow]) +"\n");
//                }
////                List<byte[]> rowRequestPayload = Collections.singletonList(rowByteBuffer.array());
////                DataPacketHeader rowRequestHeader = new DataPacketHeader(
////                    encodeTaskId, getPtoDesc().getPtoId(), LWSingleCpPirDesc.PtoStep.SERVER_SEND_ROW_STREAM_DATABASE_REQUEST.ordinal(), extraInfo,
////                    rpc.ownParty().getPartyId(), otherParty().getPartyId()
////                );
////                rpc.send(DataPacket.fromByteArrayList(rowRequestHeader, rowRequestPayload));
////                System.out.print("server send 第一次加密的结果 " +"extraInfo is "+extraInfo+"\n");
////
////                // receive response
////                DataPacketHeader medResponseHeader = new DataPacketHeader(
////                    encodeTaskId, getPtoDesc().getPtoId(), LWSingleCpPirDesc.PtoStep.CLIENT_SEND_MED_STREAM_DATABASE_RESPONSE.ordinal(), extraInfo,
////                    otherParty().getPartyId(), rpc.ownParty().getPartyId()
////                );
////                List<byte[]> medResponsePayload = rpc.receive(medResponseHeader).getPayload();
////                System.out.print("server receive 第二次加密的结果"  +"extraInfo is "+extraInfo+"\n");
////                MpcAbortPreconditions.checkArgument(medResponsePayload.size() == 1);
////                byte[] medDataByteArray = medResponsePayload.get(0);
////                // each med contains encrypted key +(random IV + encrypted value)
////                MpcAbortPreconditions.checkArgument(
////                    medDataByteArray.length == ( byteL) * columnNum
////                );
////                // split the stream database
//                ByteBuffer medByteBuffer = rprep(1, rowByteBuffer,columnNum);
//                //ByteBuffer medByteBuffer = ByteBuffer.wrap(medDataByteArray);
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//
//
//                    medByteBuffer.get(finaldatabase[iRow * columnNum + iColumn]);
//                   System.out.print("2server finaldatabase["+ (iRow+iColumn)+ "] = " + Arrays.toString(finaldatabase[iRow+iColumn]) +"\n");
//                }
//                extraInfo++;
//            }
////            for (int x = 0; x < dataNum; x++ ){
////                System.out.print("server finaldatabase["+ x+ "] = " + Arrays.toString(finaldatabase[x]) +"\n");
////
////            }
//
//            stopWatch.stop();
//            long streamColumnTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//            stopWatch.reset();
//            logStepInfo(PtoState.INIT_STEP, 3, 3, streamColumnTime, "ning Server second handles " + columnNum + " columns");
//
//        }
//
//        else if (bufferNum<sqrt(n) && bufferNum >= cbrt(n)){
//            stopWatch.start();
//            while(!isPrime(bufferNum)){
//                bufferNum = bufferNum + 1;
//            }
//            int product = bufferNum,r=1,w,rowPow,columnPow;
//            while (product < n) { //计算r
//                r=r+1;
//                product = product *bufferNum;
//
//            }
//            w = (int) Math.ceil((double)n / Math.pow(bufferNum,r-1));
//
//            rowPow = (int) Math.floor((double)r / 2);
//
//            rowNum = (int) Math.pow(bufferNum,rowPow);
//            columnPow = r-rowPow;
//            columnNum =(int)  Math.pow(bufferNum,r-rowPow-1)*w;
//            dataNum = rowNum * columnNum;
//            byte[][] firstEncResult = new byte[rowNum*columnNum][byteL];
//            // stream handling rows
//            for (int iRow = 0; iRow < rowNum; iRow++) {
//                // concatenate each column into a whole column
//                ByteBuffer rowByteBuffer = ByteBuffer.allocate(byteL * columnNum);
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//                    rowByteBuffer.put(paddingDatabasening.getBytesData(iRow * columnNum + iColumn));
//                }
//                List<byte[]> rowRequestPayload = Collections.singletonList(rowByteBuffer.array());
//
//
//
//                byte[] rowDataByteArray = rowRequestPayload.get(0);
//                System.out.print(" server iRow is"  +iRow+"\n");
//                System.out.print("rowDataByteArray.length"  +rowDataByteArray.length+"\n");
//                System.out.print("byteL * columnNum"  +byteL * columnNum+"\n");
//
//                DataPacketHeader rowRequestHeader = new DataPacketHeader(
//                    encodeTaskId, getPtoDesc().getPtoId(), LWSingleCpPirDesc.PtoStep.SERVER_SEND_ROW_STREAM_DATABASE_REQUEST.ordinal(), extraInfo,
//                    rpc.ownParty().getPartyId(), otherParty().getPartyId()
//                );
//                rpc.send(DataPacket.fromByteArrayList(rowRequestHeader, rowRequestPayload));
//                System.out.print("server send database " +"extraInfo is "+extraInfo+"\n");
//
//                // receive response
//                DataPacketHeader medResponseHeader = new DataPacketHeader(
//                    encodeTaskId, getPtoDesc().getPtoId(), LWSingleCpPirDesc.PtoStep.CLIENT_SEND_MED_STREAM_DATABASE_RESPONSE.ordinal(), extraInfo,
//                    otherParty().getPartyId(), rpc.ownParty().getPartyId()
//                );
//                List<byte[]> medResponsePayload = rpc.receive(medResponseHeader).getPayload();
//                System.out.print("server receive 对行加密的结果"  +"extraInfo is "+extraInfo+ "iRow is "+  iRow+"\n");
//                MpcAbortPreconditions.checkArgument(medResponsePayload.size() == 1);
//                byte[] medDataByteArray = medResponsePayload.get(0);
//                // each med contains encrypted key +(random IV + encrypted value)
//                MpcAbortPreconditions.checkArgument(
//                    medDataByteArray.length == (byteL) * columnNum
//                );
//                // split the stream database
//                ByteBuffer medByteBuffer = ByteBuffer.wrap(medDataByteArray);
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//                    medByteBuffer.get(firstEncResult[iRow * columnNum + iColumn]);
//                    //System.out.print("firstEncResult["+ (iRow*columnNum+iColumn) + "] = " + Arrays.toString(firstEncResult[iRow*columnNum+iColumn]) +"\n");
//                }
//                extraInfo++;
//            }
//            stopWatch.stop();
//            long streamRowTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//            stopWatch.reset();
//            logStepInfo(PtoState.INIT_STEP, 2, 3, streamRowTime, "ning Server first handles " + rowNum + " rows");//ning-↓PAI_CPPIR P_1 init Step 2/3 (12ms): Server handles 1 rows
//
//            stopWatch.start();
//            finaldatabase = new byte[dataNum][ byteL];
//            //byte[][][] secondEncResult = new byte[rowNum][columnNum][CommonConstants.BLOCK_BYTE_LENGTH + byteL];
//            // stream handling columns
//            for (int iRow = 0; iRow < rowNum; iRow++) {
//                // concatenate each column into a whole column
//                ByteBuffer rowByteBuffer = ByteBuffer.allocate((byteL) * columnNum);
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//                    rowByteBuffer.put(firstEncResult[iRow * columnNum + iColumn]);
//                    //System.out.print("server firstEncResult["+ (iColumn*rowNum+iRow) + "] = " + Arrays.toString(firstEncResult[iColumn*rowNum+iRow]) +"\n");
//                }
//                List<byte[]> rowRequestPayload = Collections.singletonList(rowByteBuffer.array());
//                DataPacketHeader rowRequestHeader = new DataPacketHeader(
//                    encodeTaskId, getPtoDesc().getPtoId(), LWSingleCpPirDesc.PtoStep.SERVER_SEND_ROW_STREAM_DATABASE_REQUEST.ordinal(), extraInfo,
//                    rpc.ownParty().getPartyId(), otherParty().getPartyId()
//                );
//                rpc.send(DataPacket.fromByteArrayList(rowRequestHeader, rowRequestPayload));
//                System.out.print("server send 第一次加密的结果 " +"extraInfo is "+extraInfo+"\n");
//
//                // receive response
//                DataPacketHeader medResponseHeader = new DataPacketHeader(
//                    encodeTaskId, getPtoDesc().getPtoId(), LWSingleCpPirDesc.PtoStep.CLIENT_SEND_MED_STREAM_DATABASE_RESPONSE.ordinal(), extraInfo,
//                    otherParty().getPartyId(), rpc.ownParty().getPartyId()
//                );
//                List<byte[]> medResponsePayload = rpc.receive(medResponseHeader).getPayload();
//                System.out.print("server receive 第二次加密的结果"  +"extraInfo is "+extraInfo+"\n");
//                MpcAbortPreconditions.checkArgument(medResponsePayload.size() == 1);
//                byte[] medDataByteArray = medResponsePayload.get(0);
//                // each med contains encrypted key +(random IV + encrypted value)
//                MpcAbortPreconditions.checkArgument(
//                    medDataByteArray.length == ( byteL) * columnNum
//                );
//                // split the stream database
//                ByteBuffer medByteBuffer = ByteBuffer.wrap(medDataByteArray);
//                for (int iColumn = 0; iColumn < columnNum; iColumn++) {
//
//
//                    medByteBuffer.get(finaldatabase[iRow * columnNum + iColumn]);
//                    // System.out.print("server finaldatabase["+ (iRow+iColumn)+ "] = " + Arrays.toString(finaldatabase[iRow+iColumn]) +"\n");
//                }
//                extraInfo++;
//            }
////            for (int x = 0; x < dataNum; x++ ){
////                System.out.print("server finaldatabase["+ x+ "] = " + Arrays.toString(finaldatabase[x]) +"\n");
////
////            }
//
//            stopWatch.stop();
//            long streamColumnTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
//            stopWatch.reset();
//            logStepInfo(PtoState.INIT_STEP, 3, 3, streamColumnTime, "ning Server second handles " + columnNum + " columns");
//
//        }

    }

    @Override
    public void pir() throws MpcAbortException {
        setPtoInput();

        DataPacketHeader queryRequestHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_QUERY.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> queryRequestPayload = rpc.receive(queryRequestHeader).getPayload();
        System.out.println("LW-server-quary-receive 1 ");
        int queryRequestSize = queryRequestPayload.size();
        MpcAbortPreconditions.checkArgument(queryRequestSize == 0 || queryRequestSize == 1);

        if (queryRequestSize == 0) {
            // response empty query
            responseEmptyQuery();
        } else {
            // response actual query
            respondActualQuery(queryRequestPayload);
        }
    }

    private void responseEmptyQuery() {
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        DataPacketHeader queryResponseHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_RESPONSE.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(queryResponseHeader, new LinkedList<>()));
        stopWatch.stop();
        long responseTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 1, responseTime, "Server responses empty query");
    }

    private void respondActualQuery(List<byte[]> queryRequestPayload) {
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        byte[] xBytes = queryRequestPayload.get(0);
        //System.out.println("server-xBytes =" + Arrays.toString(xBytes));
        int xnew = getInt(xBytes, 0);
        System.out.println("server-xnew =" + xnew);

        List<byte[]> queryResponsePayload = Collections.singletonList(finaldatabase[xnew]);
        DataPacketHeader queryResponseHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_RESPONSE.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(queryResponseHeader, queryResponsePayload));
        //System.out.println("server-query 返回查询结果。" );

        stopWatch.stop();
        long responseTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        long responseTime2 = stopWatch.getTime(TimeUnit.NANOSECONDS);
        stopWatch.reset();

        logStepInfo(PtoState.PTO_STEP, 1, 1, responseTime, "Server responses query");
        logStepInfo(PtoState.PTO_STEP, 1, 1, responseTime2, "Server responses query in NANOSECONDS");

        logPhaseInfo(PtoState.PTO_END);
    }
}
