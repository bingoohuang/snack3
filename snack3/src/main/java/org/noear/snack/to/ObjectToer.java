package org.noear.snack.to;

import org.noear.snack.ONode;
import org.noear.snack.ONodeData;
import org.noear.snack.OValue;
import org.noear.snack.OValueType;
import org.noear.snack.core.Context;
import org.noear.snack.core.exts.EnumWrap;
import org.noear.snack.core.exts.FieldWrap;
import org.noear.snack.core.utils.BeanUtil;
import org.noear.snack.core.utils.StringUtil;
import org.noear.snack.core.utils.TypeUtil;
import org.noear.snack.core.Constants;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 对象转换器
 * <p>
 * 将 ONode 转为 java Object
 */
public class ObjectToer implements Toer {
    @Override
    public void handle(Context ctx) throws Exception {
        if (null != ctx.node && null != ctx.type) {
            ctx.object = analyse(ctx.config, ctx.node, ctx.type, ctx.type);
        }
    }

    private Class<?> getTypeByNode(Constants cfg, ONode o, Class<?> def) {
        String typeStr = null;
        if (o.isArray() && o.count() == 2) {
            ONode o1 = o.get(0);
            if (o1.count() == 1) { //如果只有一个成员，则可能为list的类型节点
                o = o1;
            }
        }

        if (o.isObject()) {
            typeStr = o.get(cfg.type_key).getString();
        }

        if (StringUtil.isEmpty(typeStr) == false) {
            Class<?> clz = BeanUtil.loadClass(typeStr);
            if (clz == null) {
                throw new RuntimeException("unsupport type " + typeStr);
            } else {
                return clz;
            }
        } else {
            return def;
        }
    }

    private Object analyse(Constants cfg, ONode o, Class<?> clz, Type type) throws Exception {
        if (o == null) {
            return null;
        }

        if (ONode.class.isAssignableFrom(clz)) {
            return o;
        }

        switch (o.nodeType()) {
            case Value:
                return analyseVal(cfg, o.nodeData(), clz);
            case Object:
                clz = getTypeByNode(cfg, o, clz);
                o.remove(cfg.type_key);//尝试移除类型内容

                if (Map.class.isAssignableFrom(clz)) {
                    return analyseMap(cfg, o, clz, type);
                } else if(StackTraceElement.class.isAssignableFrom(clz)){
                    return new StackTraceElement(
                            o.get("declaringClass").getString(),
                            o.get("methodName").getString(),
                            o.get("fileName").getString(),
                            o.get("lineNumber").getInt());
                } else {
                    return analyseBean(cfg, o, clz);
                }
            case Array:
                clz = getTypeByNode(cfg, o, clz);

                if (clz.isArray()) {
                    return analyseArray(cfg, o.nodeData(), clz);
                } else {
                    return analyseCollection(cfg, o, clz, type);
                }
            default:
                return null;
        }
    }

    private boolean is(Class<?> s, Class<?> t){
        return s.isAssignableFrom(t);
    }

    public Object analyseVal(Constants cfg, ONodeData d, Class<?> clz) throws Exception {

        OValue v = d.value;

        if(v.type() == OValueType.Null){
            return null;
        }

        if (is(Byte.class, clz) || clz == Byte.TYPE) {
            return (byte) v.getLong();
        } else if (is(Short.class, clz) || clz == Short.TYPE) {
            return v.getShort();
        } else if (is(Integer.class, clz) || clz == Integer.TYPE) {
            return v.getInt();
        } else if (is(Long.class, clz) || clz == Long.TYPE) {
            return v.getLong();
        } else if (is(Float.class, clz) || clz == Float.TYPE) {
            return v.getFloat();
        } else if (is(Double.class, clz) || clz == Double.TYPE) {
            return v.getDouble();
        } else if (is(Boolean.class, clz) || clz == Boolean.TYPE) {
            return v.getBoolean();
        } else if (is(Character.class, clz) || clz == Character.TYPE) {
            return v.getChar();
        } else if (is(String.class, clz)) {
            return v.getString();
        } else if (is(java.sql.Timestamp.class, clz)) {
            return new java.sql.Timestamp(v.getLong());
        } else if (is(java.sql.Date.class, clz)) {
            return new java.sql.Date(v.getLong());
        } else if (is(java.sql.Time.class, clz)) {
            return new java.sql.Time(v.getLong());
        } else if (is(Date.class, clz)) {
            return v.getDate();
        } else if (is(BigDecimal.class, clz)) {
            if(v.type() == OValueType.Bignumber){
                return v.getRawBignumber();
            }else {
                return new BigDecimal(v.getString());
            }
        } else if (is(BigInteger.class, clz)) {
            if(v.type() == OValueType.Bignumber){
                return v.getRawBignumber();
            }else {
                return new BigInteger(v.getString());
            }
        } else if (clz.isEnum()) {
            return analyseEnum(cfg, d, clz);
        } else if (is(Class.class, clz)) {
            return BeanUtil.loadClass(v.getString());
        } else if (is(Object.class, clz)) {
            return v.getRaw();
        } else {
            throw new RuntimeException("unsupport type "+ clz.getName());
        }
    }

    public Object analyseEnum(Constants cfg, ONodeData d, Class<?> target){
        EnumWrap ew = TypeUtil.createEnum(target);
        if(d.value.type() == OValueType.String){
            return ew.get(d.value.getString());
        }else{
            return ew.get(d.value.getInt());
        }
    }

    public Object analyseArray(Constants cfg, ONodeData d, Class<?> target) throws Exception {
        int len = d.array.size();

        if (is(byte[].class, target)) {
            byte[] val = new byte[len];
            for (int i = 0; i < len; i++) {
                val[i] = (byte) d.array.get(i).getLong();
            }
            return val;
        } else if (is(short[].class, target)) {
            short[] val = new short[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getShort();
            }
            return val;
        } else if (is(int[].class, target) ) {
            int[] val = new int[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getInt();
            }
            return val;
        } else if (is(long[].class, target)) {
            long[] val = new long[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getLong();
            }
            return val;
        }  else if (is(float[].class, target) ) {
            float[] val = new float[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getFloat();
            }
            return val;
        } else if (is(double[].class, target)) {
            double[] val = new double[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getDouble();
            }
            return val;
        } else if (is(boolean[].class, target)) {
            boolean[] val = new boolean[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getBoolean();
            }
            return val;
        } else if (is(char[].class, target)) {
            char[] val = new char[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getChar();
            }
            return val;
        } else if (is(String[].class, target)) {
            String[] val = new String[len];
            for (int i = 0; i < len; i++) {
                val[i] = d.array.get(i).getString();
            }
            return val;
        } else if (is(Object[].class, target)) {
            Class<?> c = target.getComponentType();
            Object[] val = (Object[])Array.newInstance(c,len);
            for (int i = 0; i < len; i++) {
                val[i] = analyse(cfg, d.array.get(i), c,c);
            }
            return val;
        } else {
            throw new RuntimeException("unsupport type " + target.getName());
        }
    }


    public Object analyseCollection(Constants cfg, ONode o, Class<?> clz, Type type) throws Exception {
        Collection list = TypeUtil.createCollection(clz, false);

        if(list == null){
            return null;
        }

        Type itemType = TypeUtil.getCollectionItemType(type);

        if(o.count()==2) {
            ONode o1 = o.get(0);
            if (o1.count() == 1 && o1.contains(cfg.type_key)) { //说明，是有类型的集合
                o = o.get(1); //取第二个节点，做为数据节点（第1个为类型节点）;
            }
        }

        for(ONode o1 : o.nodeData().array){
            list.add(analyse(cfg,o1,(Class<?>) itemType,itemType));
        }
        return list;
    }


    public Object analyseMap(Constants cfg, ONode o, Class<?> clz, Type type) throws Exception {
        Map<Object, Object> map = TypeUtil.createMap(clz);

        if (type instanceof ParameterizedType) { //这里还要再研究下
            ParameterizedType ptt = ((ParameterizedType)type);
            Type kType = ptt.getActualTypeArguments()[0];
            Type vType = ptt.getActualTypeArguments()[1];

            if(kType instanceof ParameterizedType){
                kType = ((ParameterizedType)kType).getRawType();
            }

            if(vType instanceof ParameterizedType){
                vType = ((ParameterizedType)vType).getRawType();
            }

            if (kType == String.class) {
                for (Map.Entry<String, ONode> kv : o.nodeData().object.entrySet()) {
                    map.put(kv.getKey(), analyse(cfg, kv.getValue(), (Class<?>) vType, vType));
                }
            } else {
                for (Map.Entry<String, ONode> kv : o.nodeData().object.entrySet()) {
                    map.put(TypeUtil.strTo(kv.getKey(), (Class<?>) kType), analyse(cfg, kv.getValue(), (Class<?>) vType,vType));
                }
            }
        } else {
            for (Map.Entry<String, ONode> kv : o.nodeData().object.entrySet()) {
                map.put(kv.getKey(), analyse(cfg, kv.getValue(), Object.class,Object.class));
            }
        }

        return map;
    }


    public Object analyseBean(Constants cfg, ONode o, Class<?> target) throws Exception{
        if(is(SimpleDateFormat.class,target)){
            return new SimpleDateFormat(o.get("val").getString());
        }

        if(is(InetSocketAddress.class,target)){
            return new InetSocketAddress(o.get("address").getString(),o.get("port").getInt());
        }

        Object rst = null;
        try {
            rst = target.newInstance();
        }catch (Exception ex){
            throw new Exception("create instance error, class "+ target.getName());
        }

        // 遍历每个字段
        for (FieldWrap f : BeanUtil.getAllFields(target)) {
            String key = f.name();

            if(o.contains(key)) {
                f.set(rst, analyse(cfg, o.get(key), f.clz, f.type));
            }
        }
        return rst;
    }
}
