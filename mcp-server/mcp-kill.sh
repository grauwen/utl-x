#
#lsof -ti:3001 | xargs kill
ps -ef | grep mcp | grep -v grep | awk '{print $2}' | xargs kill
