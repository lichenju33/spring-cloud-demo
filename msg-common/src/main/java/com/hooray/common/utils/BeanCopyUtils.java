package com.hooray.common.utils;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bean复制<br>
 */
public class BeanCopyUtils {

    private static Map<String, Map<String, PropertyDescriptor>> cache = new ConcurrentHashMap<>();

    private static Map<String, List<String>> fieldCache = new HashMap<>();
    
    /**
     * 值复制
     *
     * @param src
     * @param dest
     * @param setDefaultValForNull 是否为null值属性设置默认值（null=>0,null=>""）
     * @return
     * @throws BeanConverterException
     */
    public static <T> T copy(Object src, T dest, boolean setDefaultValForNull) throws BeanConverterException {
        if (src == null) {
            return null;
        }

        try {
            Class<? extends Object> destClass = dest.getClass();
            Map<String, PropertyDescriptor> srcDescriptors = getCachePropertyDescriptors(src.getClass());
            Map<String, PropertyDescriptor> destDescriptors = getCachePropertyDescriptors(destClass);

            Set<String> keys = destDescriptors.keySet();
            for (String key : keys) {
                PropertyDescriptor srcDescriptor = srcDescriptors.get(key);

                if (srcDescriptor == null) {
                    continue;
                }

                PropertyDescriptor destDescriptor = destDescriptors.get(key);

                Object value = srcDescriptor.getReadMethod().invoke(src);

                Class<?> propertyType = destDescriptor.getPropertyType();

                Method writeMethod = destDescriptor.getWriteMethod();
                if (writeMethod == null) {
                    String name = destDescriptor.getName();
                    try {
                        writeMethod = destClass.getMethod("set" + name.substring(0, 1).toUpperCase() + name.substring(1), destDescriptor.getPropertyType());

                        destDescriptor.setWriteMethod(writeMethod);
                    } catch (Exception e) {
                    }
                }
                if (writeMethod != null) {
                    //类型匹配
                    boolean matched = propertyType == srcDescriptor.getPropertyType();
                    if (!matched) {
                        if (value != null || setDefaultValForNull) {
                            value = toValue(srcDescriptor, value, propertyType);
                        }
                    }
                    //设置默认值
                    if (value == null && setDefaultValForNull) {
                        if (destDescriptor.getPropertyType() == Long.class || destDescriptor.getPropertyType() == Integer.class || destDescriptor.getPropertyType() == Short.class || destDescriptor.getPropertyType() == Double.class || destDescriptor.getPropertyType() == Float.class) {
                            value = 0;
                        } else if (destDescriptor.getPropertyType() == String.class) {
                            value = "";
                        } else if (destDescriptor.getPropertyType() == BigDecimal.class) {
                            value = new BigDecimal("0");
                        }
                    }

                    if (value != null) {
                        writeMethod.invoke(dest, value);
                    }
                }
            }

            return dest;
        } catch (Exception e) {
            throw new BeanConverterException(e);
        }
    }

    public static <T> T copy(Object src, T dest) throws BeanConverterException {
        return copy(src, dest, false);
    }

    public static <T> List<T> copy(List<?> srcs, Class<T> destClass, boolean setDefaultValForNull) {
        if (srcs == null) {
            return new ArrayList<T>();
        }

        List<T> dests = new ArrayList<T>();
        for (Object src : srcs) {
            dests.add(copy(src, destClass, setDefaultValForNull));
        }

        return dests;
    }

    public static <T> List<T> copy(List<?> srcs, Class<T> destClass) {
        return copy(srcs, destClass, false);
    }

    public static <T> T copy(Object src, Class<T> destClass, boolean setDefaultValForNull) throws BeanConverterException {
        if (src == null) {
            return null;
        }

        try {
            T dest = destClass.newInstance();
            copy(src, dest, setDefaultValForNull);
            return dest;
        } catch (Exception e) {
            throw new BeanConverterException(e);
        }
    }

    public static <T> T copy(Object src, Class<T> destClass) throws BeanConverterException {
        return copy(src, destClass, false);
    }

    /**
     * 把对象值为0的包装类型属性转为null
     *
     * @param bean
     * @param excludeFields 排除不处理的字段
     * @throws BeanConverterException
     */
    public static void zeroWrapPropertiesToNull(Object bean, String... excludeFields) throws BeanConverterException {
        try {
            Map<String, PropertyDescriptor> srcDescriptors = getCachePropertyDescriptors(bean.getClass());
            Set<String> keys = srcDescriptors.keySet();

            List<String> excludeFieldsList = null;
            if (excludeFields != null && excludeFields.length > 0 && StringUtils.isNotBlank(excludeFields[0])) {
                excludeFieldsList = Arrays.asList(excludeFields);
            }

            for (String key : keys) {
                PropertyDescriptor srcDescriptor = srcDescriptors.get(key);
                if (srcDescriptor == null) {
                    continue;
                }
                if (excludeFieldsList != null && excludeFieldsList.contains(key)) {
                    continue;
                }
                Object value = srcDescriptor.getReadMethod().invoke(bean);

                boolean isWrapType = srcDescriptor.getPropertyType() == Long.class || srcDescriptor.getPropertyType() == Integer.class || srcDescriptor.getPropertyType() == Short.class || srcDescriptor.getPropertyType() == Double.class || srcDescriptor.getPropertyType() == Float.class;
                if (isWrapType && value != null && Integer.parseInt(value.toString()) == 0) {
                    value = null;
                    Method writeMethod = srcDescriptor.getWriteMethod();
                    if (writeMethod != null) {
                        writeMethod.invoke(bean, value);
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new BeanConverterException(e);
        }
    }

    private static Object toValue(PropertyDescriptor srcDescriptor, Object value, Class<?> propertyType) {
        
    	if (propertyType == BigDecimal.class) {
            value = (value == null) ? new BigDecimal("0") : new BigDecimal(value.toString());
        } else if (propertyType == byte.class || propertyType == Byte.class) {
            value = (value == null) ? Byte.valueOf("0") : Byte.valueOf(value.toString());
        } else if (propertyType == short.class || propertyType == Short.class) {
            value = (value == null) ? Short.valueOf("0") : Short.valueOf(value.toString());
        } else if (propertyType == int.class || propertyType == Integer.class) {
            if (srcDescriptor.getPropertyType() == boolean.class || srcDescriptor.getPropertyType() == Boolean.class) {
                value = Boolean.parseBoolean(value.toString()) ? 1 : 0;
            } else {
                value = (value == null) ? Integer.valueOf("0") : Integer.valueOf(value.toString());
            }
        } else if (propertyType == double.class || propertyType == Double.class) {
            value = (value == null) ? Double.valueOf("0") : Double.valueOf(value.toString());
        } else if (propertyType == Date.class) {
        	if(value != null){
        		if(srcDescriptor.getPropertyType() == String.class){
        			value = DateUtils.parseDate(value.toString());
        		}else if (srcDescriptor.getPropertyType() == Long.class || srcDescriptor.getPropertyType() == Integer.class || srcDescriptor.getPropertyType() == long.class || srcDescriptor.getPropertyType() == int.class) {
                    Long val = Long.valueOf(value.toString());
                    if (val.longValue() != 0) {
                        value = new Date(val);
                    } else {
                        value = null;
                    }
                }
        	}
            
        } else if (propertyType == String.class && srcDescriptor.getPropertyType() != String.class) {
            if (value != null) {
            	if(srcDescriptor.getPropertyType() == Date.class){
            		value = DateUtils.format((Date)value);
            	}else{            		
            		value = value.toString();
            	}
            }
        } else if (propertyType == boolean.class || propertyType == Boolean.class) {
            if (value.toString().matches("[0|1]")) {
                value = "1".equals(value.toString());
            }
        }else{
            value = BeanCopyUtils.copy(value, propertyType);
        }


        return value;
    }

    private static Object stringConvertTo(String value,Class<?> propertyType){
    	Object result = value;
    	if (propertyType == BigDecimal.class) {
    		result = new BigDecimal(value);
        } else if (propertyType == byte.class || propertyType == Byte.class) {
        	result = Byte.valueOf(value);
        } else if (propertyType == short.class || propertyType == Short.class) {
        	result = Short.valueOf(value.toString());
        } else if (propertyType == int.class || propertyType == Integer.class) {
        	result = Integer.parseInt(value);
        } else if (propertyType == double.class || propertyType == Double.class) {
        	result = Double.valueOf(value.toString());
        } else if (propertyType == Date.class) {
        	if(value != null){
        		result = DateUtils.parseDate(value);
        	}
            
        } else if (propertyType == boolean.class || propertyType == Boolean.class) {
        	result = Boolean.parseBoolean(value);
        } 
    	return result;
    }

    public static <T> T mapToBean(Map<String, Object> map, Class<T> clazz) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        
        try {	   	
        	T bean = clazz.newInstance();
        	Map<String, PropertyDescriptor> descriptors = getCachePropertyDescriptors(clazz);
        	for (PropertyDescriptor descriptor : descriptors.values()) {
        		String propertyName = descriptor.getName();
        		if(map.containsKey(propertyName)){
        			Object object = map.get(propertyName);
					if(object == null) {
                        continue;
                    }
					if(object instanceof String){						
						object = stringConvertTo(object.toString(),descriptor.getPropertyType());
					}
        			descriptor.getWriteMethod().invoke(bean, object);
        		}
        	}
        	return bean;
		} catch (Exception e) {
			throw new BeanConverterException(e);
		}
    }

    public static Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> returnMap = new HashMap<String, Object>();
        try {
            Map<String, PropertyDescriptor> descriptors = getCachePropertyDescriptors(bean.getClass());
            for (PropertyDescriptor descriptor : descriptors.values()) {
                String propertyName = descriptor.getName();
                Method readMethod = descriptor.getReadMethod();
                Object result = readMethod.invoke(bean, new Object[0]);
                if (result != null) {
                    returnMap.put(propertyName, result);
                }
            }
        } catch (Exception e) {
            throw new BeanConverterException(e);
        }


        return returnMap;

    }

    private static  Map<String, PropertyDescriptor> getCachePropertyDescriptors(Class<?> clazz) throws IntrospectionException {
        String canonicalName = clazz.getCanonicalName();
        Map<String, PropertyDescriptor> map = cache.get(canonicalName);

        if (map == null) {
            map = doCacheClass(clazz, canonicalName);
        }

        return map;
    }
    
    @SuppressWarnings("unused")
	private static List<String> getClassFields(Class<?> clazz) throws IntrospectionException{
    	String canonicalName = clazz.getCanonicalName();
    	
    	if(!fieldCache.containsKey(canonicalName)){
    		doCacheClass(clazz, canonicalName);
    	}
    	return fieldCache.get(canonicalName);
    }

	/**
	 * @param clazz
	 * @param canonicalName
	 * @return
	 * @throws IntrospectionException
	 */
	private synchronized static Map<String, PropertyDescriptor> doCacheClass(Class<?> clazz, String canonicalName)
			throws IntrospectionException {
		if(cache.containsKey(canonicalName)) {
            return cache.get(canonicalName);
        }
		
		Map<String, PropertyDescriptor> map = new ConcurrentHashMap<>();
		
		List<String> fieldNames = new ArrayList<>();

		BeanInfo srcBeanInfo = Introspector.getBeanInfo(clazz);

		PropertyDescriptor[] descriptors = srcBeanInfo.getPropertyDescriptors();
		for (PropertyDescriptor descriptor : descriptors) {
			
			fieldNames.add(descriptor.getName());
			
		    Method readMethod = descriptor.getReadMethod();
		    Method writeMethod = descriptor.getWriteMethod();

		    String name = descriptor.getName();

		    if (readMethod == null) {
                try {
                    readMethod = clazz.getMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));

                    descriptor.setReadMethod(readMethod);
                } catch (NoSuchMethodException | SecurityException e) {
                }
            }

		    if (writeMethod == null) {
                try {
                    writeMethod = clazz.getMethod("set" + name.substring(0, 1).toUpperCase() + name.substring(1), descriptor.getPropertyType());

                    descriptor.setWriteMethod(writeMethod);
                } catch (NoSuchMethodException | SecurityException e) {
                }
            }

		    if (readMethod != null && writeMethod != null) {
		        map.put(descriptor.getName(), descriptor);
		    }
		}

		cache.put(canonicalName, map);
		fieldCache.put(canonicalName, fieldNames);
		return map;
	}
    
    
    /**
     * 判断是否基本类型
     * @param clazz
     * @return
     * @throws Exception
     */
   public static boolean isSimpleDataType(Object o) {   
	   Class<? extends Object> clazz = o.getClass();
       return 
       (   
           clazz.equals(String.class) ||   
           clazz.equals(Integer.class)||   
           clazz.equals(Byte.class) ||   
           clazz.equals(Long.class) ||   
           clazz.equals(Double.class) ||   
           clazz.equals(Float.class) ||   
           clazz.equals(Character.class) ||   
           clazz.equals(Short.class) ||   
           clazz.equals(BigDecimal.class) ||     
           clazz.equals(Boolean.class) ||   
           clazz.equals(Date.class) ||   
           clazz.isPrimitive()   
       );   
   }


    public static class BeanConverterException extends RuntimeException {
        private static final long serialVersionUID = 152873897614690397L;

        public BeanConverterException(Throwable cause) {
            super(cause);
        }
    }
    
   //================================复制非空属性=========================================



   public static void copyPropertiesNotNull(Object dest, Object orig) throws InvocationTargetException, IllegalAccessException {
       NullAwareBeanUtilsBean.getInstance().copyProperties(dest, orig);
   }

    private static class NullAwareBeanUtilsBean extends BeanUtilsBean {

        private static NullAwareBeanUtilsBean nullAwareBeanUtilsBean;

        NullAwareBeanUtilsBean() {
            super(new ConvertUtilsBean(), new PropertyUtilsBean() {
                @Override
                public PropertyDescriptor[] getPropertyDescriptors(Class<?> beanClass) {
                    return BeanUtils.getPropertyDescriptors(beanClass);
                }

                @Override
                public PropertyDescriptor getPropertyDescriptor(Object bean, String name) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
                    return BeanUtils.getPropertyDescriptor(bean.getClass(), name);
                }
            });
        }

        public static NullAwareBeanUtilsBean getInstance() {
            if (nullAwareBeanUtilsBean == null) {
                nullAwareBeanUtilsBean = new NullAwareBeanUtilsBean();
            }
            return nullAwareBeanUtilsBean;
        }

        @Override
        public void copyProperty(Object bean, String name, Object value) throws IllegalAccessException, InvocationTargetException {
            if (value == null) {
                return;
            }
            super.copyProperty(bean, name, value);
        }
    }
}
