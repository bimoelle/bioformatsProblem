export CLASSPATH=""

# old version
O1=alt/ij-1.51n.jar
O2=alt/formats-api-5.7.0.jar
O3=alt/formats-bsd-5.7.0.jar
O4=alt/guava-21.0.jar
O5=alt/kryo-2.24.0.jar
O6=alt/slf4j-api-1.7.25.jar
O7=alt/ome-common-5.3.2.jar
O8=alt/ome-xml-5.5.4.jar
O9=alt/ome-codecs-0.2.0.jar

export CLASSPATH=".:$O1:$O2:$O3:$O4:$O5:$O6:$O7:$O8:$O9"

echo "==================:"
echo "Old Configuration:"
echo "==================:"
echo ""
echo "Classpath = $CLASSPATH"
echo ""

rm test.tif
rm Problem.class

javac Problem.java

java Problem 

echo ""
echo ""

tiffinfo test.tif

export CLASSPATH=""

echo ""
echo ""
echo ""
echo ""

# new version
J1=neu/ij-1.51s.jar
J2=neu/formats-api-5.7.2.jar
J3=neu/formats-bsd-5.7.2.jar
J4=neu/guava-21.0.jar
J5=neu/kryo-2.24.0.jar
J6=neu/slf4j-api-1.7.25.jar
### This jar causes trouble! 
J7=neu/ome-common-5.3.3.jar
### Taking the old version fixes the issue...
#J7=alt/ome-common-5.3.2.jar
###
J8=neu/ome-xml-5.6.0.jar
J9=neu/ome-codecs-0.2.0.jar

export CLASSPATH=".:$J1:$J2:$J3:$J4:$J5:$J6:$J7:$J8:$J9"

echo "==================:"
echo "New Configuration:"
echo "==================:"
echo ""
echo "Classpath = $CLASSPATH"
echo ""

rm test.tif
rm Problem.class

javac Problem.java

java Problem 

echo ""
echo ""

tiffinfo test.tif

