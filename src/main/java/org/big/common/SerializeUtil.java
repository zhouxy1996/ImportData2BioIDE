package org.big.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
/**
 * @Description: 序列化反序列化工具
 * @author BIGIOZ
 *
 */
public class SerializeUtil {
	/**
	 * 
	 * @Description 序列化
	 * @param obj
	 * @return
	 * @author ZXY
	 */
    public static byte[] serialize(Object obj){
        
        ObjectOutputStream oos = null;
        ByteArrayOutputStream baos = null;
        
        try {
            //序列化
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            
            oos.writeObject(obj);
            byte[] byteArray = baos.toByteArray();
            return byteArray;
            
        } catch (IOException e) {
            e.printStackTrace();
        }    
        return null;
    }
    
   /**
    * 
    * @Description 反序列化
    * @param bytes
    * @return
    * @author ZXY
    */
    public static Object unSerialize(byte[] bytes){
        
        ByteArrayInputStream bais = null;
        
        try {
            //反序列化为对象
            bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}