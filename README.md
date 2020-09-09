# COMP90015-Project
COMP90015 Distributed Systems Project

Small change added to line 102 in serverManager.java to catch EndpointUnavailable exception because we could not compile it otherwise.

For task 4, some assumptions are made for the expected behaviour:
1. If connection is refused, client will try to reconnect up until 10 attempts.
2. However, if the connection is successfully established, the number of attempts will reset to 0. This includes the situation where the connection is establish, then for whatever reason the connection is lost midway due to invalid message, disconnected abruptly, etc.
