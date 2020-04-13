#!/bin/bash
echo "
                .-~~~~~~~~~-._       _.-~~~~~~~~~-.
            __.'              ~.   .~              \`.__
          .'//                  \\./                  \\\\\`.
        .'//                     |                     \\\\\`.
      .'// .-~\"\"\"\"\"\"\"~~~~-._     |     _,-~~~~\"\"\"\"\"\"\"~-. \\\\\`.
    .'//.-\"                 \`-.  |  .-'                 \"-.\\\\\`.
  .'//______.============-..   \\ | /   ..-============.______\\\\\`.
.'______________________________\\|/______________________________\`.
"
apt-get update && apt-get install git lolcat tmux leiningen -y && git clone https://github.com/liszper/one-validator-dashboard.git
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
echo "I don't see the point" > key.pass
./hmy keys generate-bls-key --passphrase-file key.pass
KEY=$(find . -maxdepth 1 -type f -iname "*.key" | head -1)
NAME="$(basename -- $KEY)"
mv key.pass "$NAME.pass"
curl -LO https://raw.githubusercontent.com/harmony-one/harmony/master/scripts/node.sh && chmod a+x node.sh
cd one-validator-dashboard
echo "
_______________________________________________________________________
|[] AmigaShell                                                    |F]|!\"|
|\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"|\"|
|12.Workbench:> cd harmony:one                                        | |
|12.Work:Harmony/ONE> ed run-node.asc                                 | |
|                                                                     | |
|                                                                     |_|
|_____________________________________________________________________|/|
" | lolcat
tmux new-session -d -s "nodeSession" ./run-node.sh
tmux new-session -d -s "dashboardSession" ./install.sh
echo "Wait 5 minutes and then navigate to the your.ip:3000 address in your favorite browser to initialize your validator!"
echo "You done it!"
