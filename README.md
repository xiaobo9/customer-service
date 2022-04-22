[![Docker Layers](https://images.microbadger.com/badges/image/chatopera/contact-center:develop.svg)](https://microbadger.com/images/chatopera/contact-center:develop "Get your own image badge on microbadger.com") [![Docker Version](https://images.microbadger.com/badges/version/chatopera/contact-center:develop.svg)](https://microbadger.com/images/chatopera/contact-center:develop "Get your own version badge on microbadger.com") [![Docker Pulls](https://img.shields.io/docker/pulls/chatopera/contact-center.svg)](https://hub.docker.com/r/chatopera/contact-center/) [![Docker Stars](https://img.shields.io/docker/stars/chatopera/contact-center.svg)](https://hub.docker.com/r/chatopera/contact-center/) [![Docker Commit](https://images.microbadger.com/badges/commit/chatopera/contact-center:develop.svg)](https://microbadger.com/images/chatopera/contact-center:develop "Get your own commit badge on microbadger.com")

# customer service

## 依赖的模块

### jaudiotagger

使用 `jaudiotagger` 获得音频元信息

```xml
<dependency>
    <groupId>org</groupId>
    <artifactId>jaudiotagger</artifactId>
    <version>2.0.1</version>
</dependency>
```

```java
public class Test {
    
	public static void mp3(File source , File target) throws IllegalArgumentException, InputFormatException, EncoderException {
		AudioAttributes audio = new AudioAttributes();
		Encoder encoder = new Encoder();


		audio.setCodec("libmp3lame");
		EncodingAttributes attrs = new EncodingAttributes();
		attrs.setFormat("mp3");
		attrs.setAudioAttributes(audio);

		encoder.encode(source, target, attrs);
	}
	
	public static int getMp3TrackLength(File mp3File) {  
	    try {  
	        MP3File f = (MP3File) AudioFileIO.read(mp3File);  
	        MP3AudioHeader audioHeader = (MP3AudioHeader)f.getAudioHeader();  
	        return audioHeader.getTrackLength();  
	    } catch(Exception e) {  
	    	e.printStackTrace();
	        return 0;  
	    }  
	}
}
```

### disruptor

<https://github.com/LMAX-Exchange/disruptor>

Disruptor是一个高性能的异步处理框架，一个轻量级的JMS

```xml
<dependency>
    <groupId>com.lmax</groupId>
    <artifactId>disruptor</artifactId>
    <version>3.3.6</version>
</dependency>
```

### micrometer prometheus legacy

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.1.1</version>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-spring-legacy</artifactId>
    <version>1.1.1</version>
</dependency>
```
