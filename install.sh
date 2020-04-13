#!/bin/bash
apt-get update
apt-get install lolcat -y
echo "                     ,
                    /|
                   /*|
                  /.+|
                 /* .|
                 |:+.:V
                 /+.:*.V
                |:.*.:+|
               /+.---.Z|
             ,(((/o^o/))V
            (())) . > ()))
            )(())(/~_))))(
           /((())())((()(/)
          /::)).&& (()))():@
         /:*::)'||.   /|+:::@
        /:::::( || V / |:/:::)
        V:::+/-'L|, &  |::*:/
         |::(|_  _'   _V+::|
         |*::V '-'   //,):/Ä
         |:::+| ||
		   " | lolcat
apt-get install tmux leiningen -y
./boot dev
