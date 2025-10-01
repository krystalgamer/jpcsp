package krystal;

public class Result<T>
{
    private T ok;
    private Exception exception;
    public final boolean IsOk;

    public Result(T ok)
    {
        this.IsOk = true;
        this.ok = ok;
    }

    public Result(Exception e)
    {
        this.IsOk = false;
        this.exception = e;
    }

    public T GetOk()
    {
        if (!this.IsOk)
        {
            throw new RuntimeException(this.exception);
        }

        return this.ok;
    }

    public Exception GetError()
    {
        if (this.IsOk)
        {
            throw new RuntimeException("This is OK!");
        }

        return this.exception;
    }
}
