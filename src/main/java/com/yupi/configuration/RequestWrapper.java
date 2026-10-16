package com.yupi.configuration;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;

/*
1、包装请求，使 InputStream 可以重复读取，避免 HTTP 请求读取一次就没有了
2、HTTP 请求体本质上是一个输入流，HTTP 请求体流被读取后，内部指针已到达末尾，所以只能读取一次
 */
@Slf4j
public class RequestWrapper extends HttpServletRequestWrapper {

    /*
    HttpServletRequestWrapper 是一个装饰器模式的实现类（用于对原有的 HttpServletRequest 对象进行包装和修改，而不需要修改原始请求对象的代码）
    通过继承它，我们可以重写与读取输入流相关的方法，从而改变请求的读取行为。
     */
    private final String body;

    // 构造器，用于实现多次读取
    public RequestWrapper(HttpServletRequest request) {
        super(request);
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = request.getInputStream(); BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            char[] charBuffer = new char[128];
            int bytesRead = -1;
            while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                stringBuilder.append(charBuffer, 0, bytesRead);
            }
        } catch (IOException ignored) {
        }
        body = stringBuilder.toString();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };

    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    public String getBody() {
        return this.body;
    }

}

