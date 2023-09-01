Some Maven Usage & Tricks

* 编译的Jar包无法运行，提示`no main manifest attribute`

  原因：没有给Jar包设置一个主类，Java在运行时不知道该执行哪个类的main方法。

  解决方案1/2：

  1. 执行Jar包的时候不使用`-jar`参数，而是将Jar包作为一个Class Path用`-cp`传给Java命令行，并指定你需要执行的类，比如`java -cp example.jar org.example.Main`。

  2. 打包的时候给Jar包内部放进一个清单文件，用于指定主类

     Jar包实际上就是一个Zip压缩包，所以直接将清单文件放在压缩包中的`META-INF/MANIFEST.MF`这个位置即可

     清单文件可以理解为一个Jar包的全局说明，每个说明项占用一行，格式是`Key: Value`

     * 清单文件结尾必须有一个空行，否则无法识别
     * `Main-Class`用于指定Jar包的主类

* maven-jar-plugin插件

  在Maven打包的时候设置Main-Class
  
  插件相关的配置写在pom.xml的`<plugins>`列表中，但如果我们没有额外的配置，是可以不写的，Maven默认会启用内置的那些插件，并使用默认设置。而如果我们需要给某个插件增加额外配置，则需要向plugins列表中增加`<plugin>`配置。
  
  ```xml
  <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-jar-plugin</artifactId>
      <version>3.2.0</version>
      <configuration>
          <archive>
              <manifestFile>${project.build.outputDirectory}/META-INF/MANIFEST.MF</manifestFile>
          </archive>
      </configuration>
  </plugin>
  ```
  
  其中，`${project.build.outputDirectory}`是Maven内置变量，指向项目编译输出的路径，默认是`target/`。所以这个配置文件中，maven-jar-plugin会使用`target/META-INF/MANIFEST.MF`作为Jar包的清单文件。
  
  此时需要借助另一个内置插件`maven-resources-plugin`，它的作用是将源文件中的资源resources复制到输出目录中，无需额外配置。默认情况下，它会将`src/main/resources/`目录下的文件复制到`target/`目录下。所以，我们就在`src/main/resources/`下创建一个文件夹`META-INF`，并写入清单文件`MANIFEST.MF`指定Main-Class
  
  然后再执行mvn package，得到的Jar包就可以执行了
  
  但这个方法仍然很麻烦，因为我们需要手工创建并编写清单文件，但大部分情况下我们的需求只是配置Main-Class而已。所以，我们再对maven-jar-plugin的配置进行一些修改：
  
  ```xml
  <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-jar-plugin</artifactId>
      <version>3.2.0</version>
      <configuration>
          <archive>
              <manifest>
                  <mainClass>org.example.App</mainClass>
              </manifest>
          </archive>
      </configuration>
  </plugin>
  ```
  
* maven-dependency-plugin插件

  将依赖全部打包进一个Jar包，这种Jar包称为Uber-Jar或Fat-Jar

  `maven-assembly-plugin`插件打包Fat-Jar的原理是，将所有第三方库的class文件和资源文件全部打包进Jar中。那么，如果不借助Fat-Jar，我们是否还能解决依赖无法找到的问题呢？我们可以借助Jar包的Class-Path属性来指定依赖。

  在使用java -jar来执行一个Jar包的时候，是无法再用-classpath选项指定依赖的。但Java提供了另一种方式，就是在清单文件中使用Class-Path来指定jar包的路径。

  可以使用`maven-dependency-plugin插件`将第三方依赖的Jar包全部复制到某一个目录下，再使用`maven-jar-plugin`插件打包并设置清单文件。

  ```xml
  <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-dependency-plugin</artifactId>
      <executions>
          <execution>
              <id>copy</id>
              <goals>
                  <goal>copy-dependencies</goal>
              </goals>
              <configuration>
                  <outputDirectory>
                      ${project.build.directory}/lib
                  </outputDirectory>
              </configuration>
          </execution>
      </executions>
  </plugin>
  ```

  这个配置的作用是将所有依赖全部复制到`target/lib`目录下

  ```xml
  <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-jar-plugin</artifactId>
      <configuration>
          <archive>
              <manifest>
                  <addClasspath>true</addClasspath>
                  <classpathPrefix>lib/</classpathPrefix>
                  <mainClass>org.example.App</mainClass>
              </manifest>
          </archive>
      </configuration>
  </plugin>
  ```

  这个配置的作用是打包Jar，并指定清单文件。这里面指定了清单文件的三个项:

  * mainClass 设置主类
  * addClasspath 是否将依赖的路径添加到清单文件中，也就是是否设置Class-Path
  * classpathPrefix 设置依赖路径的前缀，也就是依赖所在的相对路径

  使用`mvn dependency:copy-dependencies package`命令进行打包

* java版本问题

  编译项目的时候，我们有时候需要制定Java版本，比如编译一个Java 6下可用的Jar包，这时候需要对Maven做一些配置。

  `maven-compiler-plugin`这个插件，用来在compile阶段将Java源文件编译成class文件，通过对它的配置，可以实现控制编译过程。

  可以通过`<source>`、`<target>`这两个配置项来控制编译出的字节码兼容性，让其可以在Java 6下运行：

  ```xml
  <plugin>  
      <groupId>org.apache.maven.plugins</groupId>  
      <artifactId>maven-compiler-plugin</artifactId>  
      <configuration>
          <source>1.6</source>
          <target>1.6</target>
      </configuration> 
  </plugin>
  ```

  还可以通过`<compilerArgs>`来指定javac运行时的参数：

  ```xml
  <plugin>  
      <groupId>org.apache.maven.plugins</groupId>  
      <artifactId>maven-compiler-plugin</artifactId>  
      <configuration>
          <source>1.6</source>
          <target>1.6</target>
          <compilerArgs>
              <arg>-g:none</arg>  
          </compilerArgs> 
      </configuration> 
  </plugin>
  ```

  这段配置用于去除Java字节码中的调试信息，这样编译好的Jar包无法直接在IDEA中进行调试

  平时阅读其他项目的pom.xml时，通常不会有上述配置，而是在整个POM文件的`<property>`部分进行如下配置：

  ```xml
  <properties>
    <maven.compiler.source>1.6</maven.compiler.source>
    <maven.compiler.target>1.6</maven.compiler.target>
  </properties>
  ```

  这也是Maven下另一种指定Java虚拟机兼容性版本的方法。`<property>`是用于在Maven中设置“全局变量”的方法，在这里面设置的全局变量，在后续的POM内容以及插件中都可以通过`${maven.compiler.source}`的方式获取。