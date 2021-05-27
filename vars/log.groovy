def message(message)
{
    echo " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~  ${message} "
}

def separator()
{
    echo " ################################################################################################################ "
} 

def warning(message)
{
    echo "WARNING! ---------------------------- [ ${message} ]  "
}
def info(message)
{
    echo "INFO ---------------------------- [ ${message} ]  "
}
def error(message)
{
    echo "ERROR!! ---------------------------- [ ${message} ]  "
}

return this