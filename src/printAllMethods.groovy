/**
* @function printAllMethods
* @purpose Prints an objects class name and then list the associated class functions.
*/
// Filename: printAllMethodsExample.groovy
/* void printAllMethods( obj ){
    if( !obj ){
		println( "Object is null\r\n" );
		return;
    }
	if( !obj.metaClass && obj.getClass() ){
        printAllMethods( obj.getClass() );
		return;
    }
	def str = "class ${obj.getClass().name} functions:\r\n";
	obj.metaClass.methods.name.unique().each{ 
		str += it+"(); "; 
	}
	println "${str}\r\n";
} */
def methods = sample.declaredMethods.findAll { !it.synthetic }

println methods
