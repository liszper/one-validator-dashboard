#!/bin/bash
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
curl -LO https://harmony.one/hmycli && mv hmycli hmy && chmod +x hmy
curl -LO https://raw.githubusercontent.com/harmony-one/harmony/master/scripts/node.sh && chmod a+x node.sh
cd one-validator-dashboard
echo "
_______________________________________________________________________
|[] AmigaShell                                                    |F]|!\"|
|\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"|\"|
|12.Workbench:> cd harmony:one                                        | |
|12.Work:Harmony/ONE> ed update-node.asc                              | |
|                                                                     | |
|                                                                     |_|
|_____________________________________________________________________|/|
" | lolcat
tmux kill-session -t "nodeSession"
tmux new-session -d -s "nodeSession" ./run-node.sh
echo "Harmony updated!"
